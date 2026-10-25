package com.smartrecruit.service.impl;

import com.smartrecruit.dto.response.CandidateCvResponse;
import com.smartrecruit.entity.CandidateCv;
import com.smartrecruit.entity.CandidateProfile;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.CandidateNotFoundException;
import com.smartrecruit.exception.CvStorageException;
import com.smartrecruit.exception.InvalidCvFileException;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.repository.CandidateCvRepository;
import com.smartrecruit.repository.CandidateProfileRepository;
import com.smartrecruit.repository.UserRepository;
import com.smartrecruit.service.CandidateCvService;
import com.smartrecruit.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class CandidateCvServiceImpl implements CandidateCvService {

    /** Signature présente au début de tout fichier PDF. */
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final CandidateCvRepository cvRepository;
    private final CandidateProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final Path storageDir;
    private final long maxSizeBytes;

    public CandidateCvServiceImpl(CandidateCvRepository cvRepository,
                                  CandidateProfileRepository profileRepository,
                                  UserRepository userRepository,
                                  CurrentUserService currentUserService,
                                  @Value("${app.cv.storage-dir}") String storageDir,
                                  @Value("${app.cv.max-size-bytes:5242880}") long maxSizeBytes) {
        this.cvRepository = cvRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    @Transactional
    public CandidateCvResponse uploadMyCv(MultipartFile file) {
        validate(file);
        User user = currentUserService.getCurrentUser();
        CandidateProfile profile = profileRepository.findByUser_Id(user.getId())
                .orElseGet(() -> profileRepository.save(CandidateProfile.builder().user(user).build()));

        String storageKey = UUID.randomUUID() + ".pdf";
        Path target = resolve(storageKey);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(storageDir);
            Files.copy(in, target);
        } catch (IOException ex) {
            throw new CvStorageException("Impossible d'enregistrer le CV", ex);
        }

        try {
            CandidateCv cv = cvRepository.saveAndFlush(CandidateCv.builder()
                    .profile(profile)
                    .originalFilename(cleanFilename(file.getOriginalFilename()))
                    .storageKey(storageKey)
                    .sizeBytes(file.getSize())
                    .build());
            return toResponse(cv, user.getId());
        } catch (RuntimeException ex) {
            // Pas de fichier orphelin si l'enregistrement en base échoue.
            deleteQuietly(target);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateCvResponse getMyCv() {
        Long userId = currentUserService.getCurrentUser().getId();
        return toResponse(findCurrentCv(userId), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CvFile getCvFile(Long candidateId) {
        User current = currentUserService.getCurrentUser();
        if (current.getRole().getName() == RoleName.CANDIDATE && !current.getId().equals(candidateId)) {
            throw new AccessDeniedException("Un candidat ne peut consulter que son propre CV");
        }

        userRepository.findById(candidateId)
                .filter(user -> user.getRole().getName() == RoleName.CANDIDATE)
                .orElseThrow(() -> new CandidateNotFoundException(candidateId));

        CandidateCv cv = findCurrentCv(candidateId);
        return new CvFile(cv.getOriginalFilename(), cv.getSizeBytes(), readContent(cv));
    }

    @Override
    public byte[] readContent(CandidateCv cv) {
        try {
            return Files.readAllBytes(resolve(cv.getStorageKey()));
        } catch (IOException ex) {
            throw new CvStorageException("Impossible de lire le CV", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findStorageKeys(Long candidateId) {
        return cvRepository.findStorageKeysByUserId(candidateId);
    }

    @Override
    public void deleteFiles(List<String> storageKeys) {
        storageKeys.forEach(key -> deleteQuietly(resolve(key)));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCvFileException(HttpStatus.BAD_REQUEST, "Le fichier du CV est vide");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidCvFileException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Le CV ne doit pas dépasser " + maxSizeBytes / (1024 * 1024) + " Mo");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidCvFileException(HttpStatus.BAD_REQUEST, "Le CV doit être un fichier .pdf");
        }
        // L'extension et le Content-Type viennent du client : on vérifie le contenu réel.
        try (InputStream in = file.getInputStream()) {
            if (!Arrays.equals(in.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE)) {
                throw new InvalidCvFileException(HttpStatus.BAD_REQUEST,
                        "Le fichier n'est pas un PDF valide");
            }
        } catch (IOException ex) {
            throw new CvStorageException("Impossible de lire le fichier envoyé", ex);
        }
    }

    private CandidateCv findCurrentCv(Long userId) {
        return cvRepository.findFirstByProfile_User_IdOrderByUploadedAtDescIdDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun CV déposé pour ce candidat"));
    }

    /** Résout une clé de stockage en refusant toute sortie du dossier des CV. */
    private Path resolve(String storageKey) {
        Path path = storageDir.resolve(storageKey).normalize();
        if (!path.startsWith(storageDir)) {
            throw new CvStorageException("Clé de stockage invalide", null);
        }
        return path;
    }

    /** Garde seulement le nom du fichier (sans chemin) et le tronque à la taille de la colonne. */
    private String cleanFilename(String filename) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(filename)).replaceAll("[\\r\\n\"]", "_");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Fichier CV non supprimé après échec : {}", path.getFileName());
        }
    }

    private CandidateCvResponse toResponse(CandidateCv cv, Long candidateId) {
        return new CandidateCvResponse(cv.getId(), candidateId, cv.getOriginalFilename(),
                cv.getSizeBytes(), cv.getUploadedAt());
    }
}
