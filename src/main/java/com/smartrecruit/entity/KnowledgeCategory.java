package com.smartrecruit.entity;

/**
 * Catégories de la base de connaissances RH.
 * Une FAQ est une ressource courte, un guide une ressource plus longue,
 * mais les deux utilisent la même entité KnowledgeDocument.
 */
public enum KnowledgeCategory {
    FAQ,
    CANDIDATE_GUIDE,
    RECRUITER_GUIDE,
    INTERVIEW_GUIDE,
    RECRUITMENT_PROCEDURE,
    PLATFORM_HELP,
    HR_POLICY,
    OTHER
}
