package com.smartrecruit.service;

import com.smartrecruit.dto.ai.AiRagChatRequest;
import com.smartrecruit.dto.ai.AiRagChatResponse;
import com.smartrecruit.dto.ai.AiRagSourceResponse;
import com.smartrecruit.dto.request.HrAskRequest;
import com.smartrecruit.dto.response.AssistantMessageResponse;
import com.smartrecruit.dto.response.HrAskResponse;
import com.smartrecruit.dto.response.HrMessageResponse;
import com.smartrecruit.dto.response.MessageSourceResponse;
import com.smartrecruit.entity.AiRun;
import com.smartrecruit.entity.AiRunStatus;
import com.smartrecruit.entity.Conversation;
import com.smartrecruit.entity.Message;
import com.smartrecruit.entity.MessageRole;
import com.smartrecruit.entity.MessageSource;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ConversationNotFoundException;
import com.smartrecruit.exception.HrAssistantException;
import com.smartrecruit.integration.ai.AiServiceClient;
import com.smartrecruit.integration.ai.AiServiceException;
import com.smartrecruit.mapper.MessageMapper;
import com.smartrecruit.mapper.MessageSourceMapper;
import com.smartrecruit.repository.AiRunRepository;
import com.smartrecruit.repository.ConversationRepository;
import com.smartrecruit.repository.MessageRepository;
import com.smartrecruit.repository.MessageSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestration d'une question à l'Assistant RH.
 *
 * Le message USER et le AiRun PENDING sont enregistrés dans une transaction
 * courte. L'appel FastAPI/Ollama est effectué hors transaction. La réponse
 * ASSISTANT, ses sources et la finalisation du AiRun sont persistées dans une
 * seconde transaction courte. Aucune réponse invalide n'est enregistrée.
 */
@Service
@RequiredArgsConstructor
public class HrAssistantService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final AiRunRepository aiRunRepository;
    private final CurrentUserService currentUserService;
    private final AiServiceClient aiServiceClient;
    private final MessageMapper messageMapper;
    private final MessageSourceMapper messageSourceMapper;
    private final PlatformTransactionManager transactionManager;

    public HrAskResponse ask(Long conversationId, HrAskRequest request) {
        User user = currentUserService.getCurrentUser();
        String content = request.content().trim();
        String userRole = resolveUserRole(user);

        PreparedAsk prepared = transactionManager().execute(status -> {
            Conversation conversation = conversationRepository
                    .findByIdAndUser_Id(conversationId, user.getId())
                    .orElseThrow(ConversationNotFoundException::new);

            Message userMessage = messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role(MessageRole.USER)
                    .content(content)
                    .build());

            AiRun aiRun = aiRunRepository.save(AiRun.builder()
                    .conversationId(conversation.getId())
                    .userMessageId(userMessage.getId())
                    .status(AiRunStatus.PENDING)
                    .build());

            return new PreparedAsk(
                    conversation.getId(),
                    messageMapper.toResponse(userMessage, List.of()),
                    aiRun.getId(),
                    content,
                    userRole
            );
        });

        if (prepared == null) {
            throw new HrAssistantException(
                    "L'Assistant RH est temporairement indisponible.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_RUN_NOT_CREATED"
            );
        }

        try {
            AiRagChatResponse response = aiServiceClient.generateRagAnswer(
                    new AiRagChatRequest(
                            prepared.content(),
                            prepared.conversationId(),
                            prepared.userRole()
                    )
            );
            validateAiResponse(response);

            CompletedAsk completed = transactionManager().execute(status -> {
                Conversation conversation = conversationRepository
                        .findById(prepared.conversationId())
                        .orElseThrow(ConversationNotFoundException::new);

                Message assistantMessage = messageRepository.save(Message.builder()
                        .conversation(conversation)
                        .role(MessageRole.ASSISTANT)
                        .content(response.answer())
                        .build());

                List<MessageSource> sources = response.sources() == null
                        ? List.of()
                        : response.sources().stream()
                                .map(source -> toEntity(assistantMessage, source))
                                .toList();
                if (!sources.isEmpty()) {
                    messageSourceRepository.saveAll(sources);
                }

                AiRun aiRun = aiRunRepository.findById(prepared.aiRunId())
                        .orElseThrow(() -> new IllegalStateException("AiRun introuvable"));
                aiRun.setAssistantMessageId(assistantMessage.getId());
                aiRun.setStatus(AiRunStatus.COMPLETED);
                aiRun.setModelName(response.modelName());
                aiRun.setPromptVersion(response.promptVersion());
                aiRun.setRetrievalDurationMs(response.retrievalDurationMs());
                aiRun.setGenerationDurationMs(response.generationDurationMs());
                aiRun.setTotalDurationMs(response.totalDurationMs());
                aiRun.setPromptTokenCount(response.promptTokenCount());
                aiRun.setCompletionTokenCount(response.completionTokenCount());
                aiRun.setRetrievedChunkCount(sources.size());
                aiRun.setGrounded(response.grounded());
                aiRun.setNeedsHumanReview(response.needsHumanReview());
                aiRun.setCompletedAt(LocalDateTime.now());
                aiRunRepository.save(aiRun);

                List<MessageSourceResponse> sourceResponses =
                        messageSourceMapper.toResponses(sources);
                AssistantMessageResponse assistantResponse = new AssistantMessageResponse(
                        assistantMessage.getId(),
                        conversation.getId(),
                        assistantMessage.getRole(),
                        assistantMessage.getContent(),
                        assistantMessage.getCreatedAt(),
                        sourceResponses
                );

                return new CompletedAsk(assistantResponse, sourceResponses);
            });

            if (completed == null) {
                throw new HrAssistantException(
                        "L'Assistant RH est temporairement indisponible.",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_RUN_NOT_COMPLETED"
                );
            }

            return new HrAskResponse(
                    prepared.userMessage(),
                    completed.assistantMessage(),
                    completed.sources(),
                    response.grounded(),
                    response.needsHumanReview(),
                    response.modelName(),
                    response.promptVersion(),
                    response.totalDurationMs()
            );
        } catch (AiServiceException ex) {
            markFailed(prepared.aiRunId(), ex.getErrorCode());
            throw mapAiServiceException(ex);
        } catch (HrAssistantException ex) {
            markFailed(prepared.aiRunId(), ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            markFailed(prepared.aiRunId(), "AI_RUN_FAILED");
            throw new HrAssistantException(
                    "L'Assistant RH est temporairement indisponible. "
                            + "Votre message a été enregistré. Vous pouvez réessayer.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_RUN_FAILED"
            );
        }
    }

    private TransactionTemplate transactionManager() {
        return new TransactionTemplate(transactionManager);
    }

    private void validateAiResponse(AiRagChatResponse response) {
        if (response == null
                || !StringUtils.hasText(response.answer())
                || response.answer().length() > 4000
                || response.category() == null
                || !StringUtils.hasText(response.modelName())
                || !StringUtils.hasText(response.promptVersion())) {
            throw invalidAiResponse();
        }

        List<AiRagSourceResponse> sources = response.sources() == null
                ? List.of()
                : response.sources();
        if (response.grounded() && sources.isEmpty()) {
            throw invalidAiResponse();
        }
        if (!response.grounded() && (!sources.isEmpty() || !response.needsHumanReview())) {
            throw invalidAiResponse();
        }
        java.util.Set<String> chunkIds = new java.util.HashSet<>();
        for (AiRagSourceResponse source : sources) {
            if (source == null
                    || source.documentId() == null || source.documentId() <= 0
                    || !StringUtils.hasText(source.chunkId())
                    || !chunkIds.add(source.chunkId())
                    || source.chunkIndex() == null || source.chunkIndex() < 0
                    || !StringUtils.hasText(source.title())
                    || source.category() == null
                    || source.score() == null
                    || !Double.isFinite(source.score())
                    || source.score() < 0 || source.score() > 1
                    || source.version() == null || source.version() < 0) {
                throw invalidAiResponse();
            }
        }
    }

    private HrAssistantException invalidAiResponse() {
        return new HrAssistantException(
                "La réponse du modèle local est invalide.",
                HttpStatus.BAD_GATEWAY,
                "INVALID_AI_RESPONSE"
        );
    }

    private MessageSource toEntity(Message message, AiRagSourceResponse source) {
        String excerpt = source.excerpt();
        if (excerpt != null && excerpt.length() > 500) {
            excerpt = excerpt.substring(0, 499) + "…";
        }
        return MessageSource.builder()
                .message(message)
                .documentId(source.documentId())
                .chunkId(source.chunkId())
                .chunkIndex(source.chunkIndex())
                .title(source.title())
                .category(source.category())
                .score(source.score())
                .documentVersion(source.version())
                .excerpt(excerpt)
                .build();
    }

    private void markFailed(Long aiRunId, String errorCode) {
        try {
            transactionManager().executeWithoutResult(status ->
                    aiRunRepository.findById(aiRunId).ifPresent(aiRun -> {
                        aiRun.setStatus(AiRunStatus.FAILED);
                        aiRun.setErrorCode(errorCode);
                        aiRun.setCompletedAt(LocalDateTime.now());
                        aiRunRepository.save(aiRun);
                    })
            );
        } catch (RuntimeException ignored) {
            // La trace technique ne doit jamais masquer l'erreur métier renvoyée.
        }
    }

    private String resolveUserRole(User user) {
        RoleName roleName = user.getRole() != null ? user.getRole().getName() : null;
        if (roleName == RoleName.CANDIDATE || roleName == RoleName.RECRUITER) {
            return roleName.name();
        }
        throw new HrAssistantException(
                "L'Assistant RH est réservé aux candidats et aux recruteurs.",
                HttpStatus.FORBIDDEN,
                "ROLE_NOT_ALLOWED"
        );
    }

    private HrAssistantException mapAiServiceException(AiServiceException ex) {
        return switch (ex.getErrorCode()) {
            case "GENERATION_TIMEOUT" -> new HrAssistantException(
                    "La génération a dépassé le délai autorisé.",
                    HttpStatus.GATEWAY_TIMEOUT,
                    ex.getErrorCode()
            );
            case "OLLAMA_UNAVAILABLE" -> new HrAssistantException(
                    "Le modèle local n'est pas disponible. "
                            + "Votre message a été enregistré. Vous pouvez réessayer.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ex.getErrorCode()
            );
            case "INVALID_AI_RESPONSE" -> new HrAssistantException(
                    "La réponse du modèle local est invalide. "
                            + "Votre message a été enregistré. Vous pouvez réessayer.",
                    HttpStatus.BAD_GATEWAY,
                    ex.getErrorCode()
            );
            default -> new HrAssistantException(
                    "L'Assistant RH est temporairement indisponible. "
                            + "Votre message a été enregistré. Vous pouvez réessayer.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ex.getErrorCode()
            );
        };
    }

    private record PreparedAsk(
            Long conversationId,
            HrMessageResponse userMessage,
            Long aiRunId,
            String content,
            String userRole
    ) {
    }

    private record CompletedAsk(
            AssistantMessageResponse assistantMessage,
            List<MessageSourceResponse> sources
    ) {
    }
}
