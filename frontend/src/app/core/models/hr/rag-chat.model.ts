import type { HrMessageResponse } from './hr-message.model';
import type { MessageSourceResponse } from './message-source.model';

export interface HrAskRequest {
  content: string;
}

export interface AssistantMessageResponse extends HrMessageResponse {
  role: 'ASSISTANT';
  sources: MessageSourceResponse[];
}

export interface HrAskResponse {
  userMessage: HrMessageResponse;
  assistantMessage: AssistantMessageResponse;
  sources: MessageSourceResponse[];
  grounded: boolean;
  needsHumanReview: boolean;
  modelName: string;
  promptVersion: string;
  durationMs: number;
}
