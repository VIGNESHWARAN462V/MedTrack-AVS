import React, { useState, useRef, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import aiService from '../services/aiService';
import SEO from '../components/common/SEO';

const SUGGESTED_QUESTIONS = [
  'Where is ECG-001 currently reported?',
  'Which assets have stale GPS updates?',
  'Summarize recent unresolved geofence alerts.',
  'Which asset transfers are currently in progress?',
  'Explain the current AMC result.',
  'Which department has the greatest GUB supply deficit?',
];

export default function AiAssistantPage() {
  const { user } = useAuth();
  const [messages, setMessages] = useState([]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorBanner, setErrorBanner] = useState(null);
  const [lastFailedQuestion, setLastFailedQuestion] = useState(null);

  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  // Auto-scroll to bottom of conversation
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading, scrollToBottom]);

  // Focus input on load
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Format UTC ISO timestamp into clean local/UTC string
  const formatUtcTimestamp = (isoString) => {
    if (!isoString) return 'Unavailable';
    try {
      const d = new Date(isoString);
      return d.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        timeZoneName: 'short',
      });
    } catch {
      return isoString;
    }
  };

  // Badge styling helper for source freshness
  const getFreshnessBadgeClass = (freshness) => {
    if (!freshness) return 'bg-secondary text-white';
    const upper = freshness.toUpperCase();
    if (upper.includes('LIVE') || upper === 'MEASURED') {
      return 'bg-success text-white';
    }
    if (upper.includes('STALE') || upper.includes('IDLE')) {
      return 'bg-warning text-dark';
    }
    if (upper.includes('UNRESOLVED') || upper.includes('DEFICIT') || upper.includes('ERROR')) {
      return 'bg-danger text-white';
    }
    if (upper.includes('NO_DATA')) {
      return 'bg-secondary text-white';
    }
    return 'bg-info text-dark';
  };

  const handleSendQuestion = async (questionToSend) => {
    const text = (questionToSend || inputQuestion).trim();
    if (!text || loading) return;

    setErrorBanner(null);
    setLastFailedQuestion(null);

    const userMessage = {
      id: 'user-' + Date.now(),
      sender: 'user',
      text,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputQuestion('');
    setLoading(true);

    try {
      const response = await aiService.chat(text);

      const assistantMessage = {
        id: 'ai-' + Date.now(),
        sender: 'assistant',
        text: response.answer,
        provider: response.provider,
        model: response.model,
        answeredAt: response.answeredAt,
        sources: response.sources || [],
        dataAsOf: response.dataAsOf,
        cached: response.cached,
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      console.error('[AI Assistant] Chat request failed:', err);

      let userFacingError = 'An unexpected error occurred while communicating with the AI service.';
      let isRetryable = true;

      if (err.response) {
        const status = err.response.status;
        const errCode = err.response.data?.error;
        const message = err.response.data?.message;

        if (status === 400) {
          userFacingError = message || 'Invalid question format or parameter rejected by the server.';
          isRetryable = false;
        } else if (status === 401) {
          userFacingError = 'Your session has expired. Please log in again to continue.';
          isRetryable = false;
        } else if (status === 403) {
          userFacingError = message || 'Access denied: You do not have permission to view the requested data under your current role.';
          isRetryable = false;
        } else if (status === 429) {
          userFacingError = message || 'AI provider rate limit exceeded. Please pause for a moment before submitting your next question.';
          isRetryable = true;
        } else if (status === 502 || errCode === 'AI_MODEL_UNAVAILABLE') {
          userFacingError = message || 'The configured AI model is temporarily unavailable. Please retry.';
          isRetryable = true;
        } else if (status === 503 && errCode === 'AI_NOT_CONFIGURED') {
          userFacingError = 'The AI Operations Assistant is currently unconfigured. The server administrator must configure AI_PROVIDER and AI_API_KEY.';
          isRetryable = false;
        } else if (status === 503) {
          userFacingError = message || 'The AI provider is currently experiencing high demand. Please try again momentarily.';
          isRetryable = true;
        } else if (status === 504 || errCode === 'AI_PROVIDER_TIMEOUT') {
          userFacingError = 'The AI provider timed out while analyzing hospital records. Please retry.';
          isRetryable = true;
        } else if (message) {
          userFacingError = message;
        }
      } else if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        userFacingError = 'The request timed out before receiving a response from the server. Please check your network and retry.';
      }

      setErrorBanner(userFacingError);
      if (isRetryable) {
        setLastFailedQuestion(text);
      }
    } finally {
      setLoading(false);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendQuestion();
    }
  };

  const handleClearConversation = () => {
    setMessages([]);
    setErrorBanner(null);
    setLastFailedQuestion(null);
    setInputQuestion('');
    inputRef.current?.focus();
  };

  return (
    <div className="ai-assistant-container d-flex flex-column" style={{ minHeight: 'calc(100vh - 120px)' }}>
      <SEO title="MedTrack AI Operations Assistant | Hospital Asset Intelligence" noindex={true} />

      {/* Header & Context Bar */}
      <div className="card shadow-sm border-0 mb-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-lowest)' }}>
        <div className="card-body p-3 p-md-4">
          <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3">
            <div>
              <div className="d-flex align-items-center gap-2 mb-1">
                <span className="material-symbols-outlined fs-2 text-primary">smart_toy</span>
                <h1 className="h4 mb-0 fw-bold">MedTrack AI Operations Assistant</h1>
                <span className="badge bg-secondary-subtle text-secondary-emphasis rounded-pill px-2 py-1 fs-8">
                  Phase 20.3 Read-Only
                </span>
              </div>
              <p className="text-muted small mb-0">
                Natural-language hospital operations intelligence grounded in authorized real-time telemetry, geofence alerts, transfer states, and clinical analytics.
              </p>
            </div>

            <div className="d-flex align-items-center gap-2 flex-wrap">
              <span className="badge rounded-pill bg-light text-dark border px-3 py-2 small d-inline-flex align-items-center gap-1">
                <span className="material-symbols-outlined fs-6 text-success">lock</span>
                Role Scope: <strong>{user?.role?.name || 'STAFF'}</strong>
              </span>
              <button
                type="button"
                className="btn btn-outline-secondary btn-sm d-inline-flex align-items-center gap-1"
                onClick={handleClearConversation}
                disabled={loading || messages.length === 0}
                title="Start a new conversation"
              >
                <span className="material-symbols-outlined fs-6">refresh</span>
                New Chat
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Global Error Banner */}
      {errorBanner && (
        <div className="alert alert-danger alert-dismissible fade show d-flex align-items-center justify-content-between mb-3 shadow-sm rounded-3" role="alert">
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5 flex-shrink-0">error</span>
            <span className="small">{errorBanner}</span>
          </div>
          <div className="d-flex align-items-center gap-2 ms-3">
            {lastFailedQuestion && (
              <button
                type="button"
                className="btn btn-sm btn-outline-danger"
                onClick={() => handleSendQuestion(lastFailedQuestion)}
                disabled={loading}
              >
                Retry
              </button>
            )}
            <button
              type="button"
              className="btn-close position-static p-2"
              onClick={() => setErrorBanner(null)}
              aria-label="Close"
            />
          </div>
        </div>
      )}

      {/* Main Chat Scroll Container */}
      <div
        className="card shadow-sm border-0 flex-grow-1 d-flex flex-column rounded-3 mb-3"
        style={{
          backgroundColor: 'var(--surface-container-lowest)',
          maxHeight: 'calc(100vh - 280px)',
          minHeight: '400px',
        }}
      >
        <div className="card-body p-3 p-md-4 overflow-y-auto flex-grow-1" style={{ scrollBehavior: 'smooth' }}>
          {/* Empty State / Welcome Screen */}
          {messages.length === 0 && (
            <div className="text-center py-4 py-md-5 my-auto">
              <div
                className="d-inline-flex p-3 rounded-circle mb-3"
                style={{ backgroundColor: 'var(--secondary-container)' }}
              >
                <span className="material-symbols-outlined text-primary" style={{ fontSize: '42px' }}>
                  psychology_alt
                </span>
              </div>
              <h2 className="h5 fw-bold text-dark mb-2">How can I assist hospital operations today?</h2>
              <p className="text-muted small mx-auto mb-4" style={{ maxWidth: '580px' }}>
                Ask operational questions about equipment locations, stale tracking sessions, active transfers, or clinical balance metrics. All responses are derived strictly from authoritative MedTrack records under your server-side permissions.
              </p>

              {/* Suggested Questions Chips */}
              <div className="d-flex flex-wrap justify-content-center gap-2 mx-auto" style={{ maxWidth: '720px' }}>
                {SUGGESTED_QUESTIONS.map((q, idx) => (
                  <button
                    key={idx}
                    type="button"
                    className="btn btn-sm btn-outline-secondary rounded-pill px-3 py-2 text-start text-md-center shadow-none"
                    onClick={() => handleSendQuestion(q)}
                    disabled={loading}
                    style={{ fontSize: '0.82rem' }}
                  >
                    <span className="material-symbols-outlined fs-6 align-middle me-1 text-primary">chat_bubble</span>
                    {q}
                  </button>
                ))}
              </div>

              <div className="mt-4 pt-3 border-top mx-auto text-muted small" style={{ maxWidth: '640px', fontSize: '0.78rem' }}>
                <span className="badge bg-light text-secondary border me-2">Strictly Read-Only</span>
                The assistant cannot create, delete, or reassign assets, accept transfers, or resolve alerts.
              </div>
            </div>
          )}

          {/* Conversation Messages */}
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`d-flex mb-3 ${msg.sender === 'user' ? 'justify-content-end' : 'justify-content-start'}`}
            >
              <div
                className={`p-3 rounded-3 shadow-sm ${
                  msg.sender === 'user'
                    ? 'text-white'
                    : 'bg-light text-dark border'
                }`}
                style={{
                  maxWidth: '85%',
                  backgroundColor: msg.sender === 'user' ? 'var(--primary)' : 'var(--surface-container-low)',
                  wordBreak: 'break-word',
                }}
              >
                {/* Sender Header */}
                <div className="d-flex align-items-center justify-content-between gap-2 mb-2 pb-1 border-bottom" style={{ borderColor: msg.sender === 'user' ? 'rgba(255,255,255,0.2)' : 'var(--surface-variant)' }}>
                  <div className="d-flex align-items-center gap-1 small fw-semibold">
                    <span className="material-symbols-outlined fs-6">
                      {msg.sender === 'user' ? 'account_circle' : 'smart_toy'}
                    </span>
                    <span>{msg.sender === 'user' ? (user?.name || 'You') : 'MedTrack AI Assistant'}</span>
                  </div>

                  {msg.sender === 'assistant' && msg.provider && (
                    <span className="badge bg-secondary-subtle text-secondary-emphasis rounded-pill px-2 py-0" style={{ fontSize: '0.68rem' }}>
                      {msg.provider} • {msg.model}
                    </span>
                  )}
                </div>

                {/* Message Body */}
                <div className="message-content small" style={{ whiteSpace: 'pre-wrap', lineHeight: '1.55' }}>
                  {msg.text}
                </div>

                {/* Authoritative Sources Citations (Assistant Only) */}
                {msg.sender === 'assistant' && msg.sources && msg.sources.length > 0 && (
                  <div className="mt-3 pt-2 border-top" style={{ borderColor: 'var(--surface-variant)' }}>
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <span className="fw-bold text-uppercase text-muted" style={{ fontSize: '0.7rem', letterSpacing: '0.04em' }}>
                        Authoritative Data Sources ({msg.sources.length})
                      </span>
                      {msg.dataAsOf && (
                        <span className="text-muted" style={{ fontSize: '0.68rem' }}>
                          Telemetry as of: {formatUtcTimestamp(msg.dataAsOf)}
                        </span>
                      )}
                    </div>

                    <div className="d-flex flex-column gap-1">
                      {msg.sources.map((src, sIdx) => (
                        <div
                          key={sIdx}
                          className="p-2 rounded-2 bg-white border d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-1"
                          style={{ fontSize: '0.74rem' }}
                        >
                          <div className="d-flex align-items-center gap-2">
                            <span className="badge bg-light text-dark border">
                              {src.resourceType || 'RECORD'}
                            </span>
                            <span className="fw-semibold text-dark">{src.label}</span>
                            {src.identifier && (
                              <span className="text-muted">({src.identifier})</span>
                            )}
                          </div>
                          <div className="d-flex align-items-center gap-2 ms-auto ms-sm-0">
                            {src.timestamp && (
                              <span className="text-muted" style={{ fontSize: '0.7rem' }}>
                                {formatUtcTimestamp(src.timestamp)}
                              </span>
                            )}
                            <span className={`badge rounded-pill ${getFreshnessBadgeClass(src.freshness)}`} style={{ fontSize: '0.65rem' }}>
                              {src.freshness || 'RECORDED'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}

          {/* Assistant Loading Indicator */}
          {loading && (
            <div className="d-flex justify-content-start mb-3">
              <div
                className="p-3 rounded-3 shadow-sm bg-light border text-dark d-flex align-items-center gap-2"
                style={{ maxWidth: '85%' }}
              >
                <span className="spinner-border spinner-border-sm text-primary" role="status" aria-hidden="true" />
                <span className="small text-muted">Consulting authorized MedTrack operational records...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="card-footer p-3 bg-white border-top rounded-bottom-3">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSendQuestion();
            }}
          >
            <div className="input-group shadow-sm rounded-3">
              <input
                ref={inputRef}
                type="text"
                className="form-control py-2 px-3 border-end-0"
                placeholder="Ask an operational question (e.g., Where is ECG-001?)..."
                value={inputQuestion}
                onChange={(e) => setInputQuestion(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={loading}
                maxLength={2000}
                aria-label="Operational question input"
              />
              <button
                type="submit"
                className="btn btn-primary px-4 d-inline-flex align-items-center gap-1"
                disabled={loading || !inputQuestion.trim()}
                aria-label="Send question"
              >
                {loading ? (
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
                ) : (
                  <>
                    <span>Send</span>
                    <span className="material-symbols-outlined fs-5">send</span>
                  </>
                )}
              </button>
            </div>
          </form>
          <div className="d-flex justify-content-between align-items-center mt-2 px-1 text-muted" style={{ fontSize: '0.72rem' }}>
            <span>Press <strong>Enter</strong> to send. All responses are read-only and authorization-scoped.</span>
            <span>{inputQuestion.length}/2000</span>
          </div>
        </div>
      </div>
    </div>
  );
}
