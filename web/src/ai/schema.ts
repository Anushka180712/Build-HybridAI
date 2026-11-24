import { CATEGORIES } from './types';

/**
 * One JSON schema used by both paths: Chrome's Prompt API receives it as
 * `responseConstraint`, Gemini in the cloud receives it as `responseSchema`.
 * It is kept deliberately small so it fits Gemini Nano's context window.
 */
export const ANALYSIS_SCHEMA = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['safe', 'suspicious', 'scam'] },
    riskScore: { type: 'integer', minimum: 0, maximum: 100 },
    category: { type: 'string', enum: [...CATEGORIES] },
    summary: { type: 'string' },
    redFlags: {
      type: 'array',
      maxItems: 5,
      items: {
        type: 'object',
        properties: {
          label: { type: 'string' },
          evidence: { type: 'string' },
          why: { type: 'string' },
        },
        required: ['label', 'evidence', 'why'],
      },
    },
    reassurances: { type: 'array', maxItems: 3, items: { type: 'string' } },
    nextSteps: { type: 'array', maxItems: 4, items: { type: 'string' } },
    safeReply: { type: 'string' },
  },
  required: ['verdict', 'riskScore', 'category', 'summary', 'redFlags', 'reassurances', 'nextSteps', 'safeReply'],
} as const;

export const DOJO_SCHEMA = {
  type: 'object',
  properties: {
    sender: { type: 'string' },
    message: { type: 'string' },
    tells: { type: 'array', maxItems: 3, items: { type: 'string' } },
    explanation: { type: 'string' },
  },
  required: ['sender', 'message', 'tells', 'explanation'],
} as const;
