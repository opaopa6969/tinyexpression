package org.unlaxer.listener;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

/**
 * Listener that allows interactive control of the parsing process.
 */
public class InteractiveDebuggerListener implements ParserListener, TransactionListener {

    private final Semaphore stepSemaphore = new Semaphore(0);
    private final AtomicBoolean paused = new AtomicBoolean(true);
    private OutputLevel level = OutputLevel.simple;
    private Consumer<Object> eventBroadcaster;
    private final java.util.Deque<String> activeStack = new java.util.ArrayDeque<>();

    public void setEventBroadcaster(Consumer<Object> eventBroadcaster) {
        this.eventBroadcaster = eventBroadcaster;
    }

    private String getParserId(Parser parser) {
        if (parser == null) return "null";
        org.unlaxer.Name name = parser.getName();
        if (name != null && !name.toString().isEmpty()) {
            return name.toString();
        }
        return parser.getClass().getSimpleName() + "_" + System.identityHashCode(parser);
    }

    private void broadcast(String type, Parser parser, ParseContext parseContext, String status, String detail) {
        try {
            if (eventBroadcaster != null) {
                java.util.Map<String, Object> event = new java.util.HashMap<>();
                event.put("type", type);
                String pId = getParserId(parser);
                event.put("parserId", pId);
                if (parser != null) {
                    event.put("parserType", parser.getClass().getSimpleName());
                }
                event.put("parentParserId", activeStack.isEmpty() ? null : activeStack.peek());
                if (parseContext != null) {
                    int consumed = parseContext.getConsumedPosition().value();
                    event.put("position", consumed);
                    event.put("farthest", parseContext.farthestConsumedOffset);
                    event.put("station", parseContext.getRemain(TokenKind.consumed).toString());
                }
                event.put("status", status);
                if (detail != null) event.put("label", detail);
                eventBroadcaster.accept(event);
            }
        } catch (Exception e) {
            System.err.println("broadcast error: " + e.getMessage());
        }
    }

    public void stepForward() {
        stepSemaphore.release();
    }

    public void resume() {
        paused.set(false);
        if (stepSemaphore.availablePermits() == 0) {
            stepSemaphore.release();
        }
    }

    public void pause() {
        paused.set(true);
    }

    public void stepBack(ParseContext parseContext) {
        try {
            if (parseContext.popSnapshot()) {
                this.pause();
                activeStack.clear();
                // When we restore, we also need to restore the activeStack.
                // But Snapshot doesn't have it. 
                // We'll rely on the next ON_START events to rebuild it visually if needed.
                broadcast("STATE_RESTORED", null, parseContext, "RESTORED", "Stepped back to previous state.");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void waitIfPaused() {
        if (paused.get()) {
            try {
                stepSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onStart(Parser parser, ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
        // Snapshot logic handled in ParseContext and onCommit
        String pId = getParserId(parser);
        broadcast("ON_START", parser, parseContext, "PENDING", null);
        activeStack.push(pId);
        waitIfPaused();
    }

    @Override
    public void onEnd(Parser parser, Parsed parsed, ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
        try {
            if (!activeStack.isEmpty()) activeStack.pop();
            String error = null;
            if (parsed != null && parsed.isFailed() && parseContext != null) {
                error = "Failed to match " + parser.getClass().getSimpleName() + " at pos " + parseContext.getConsumedPosition().value();
            }
            broadcast("ON_END", parser, parseContext, (parsed != null ? parsed.status.name() : "UNKNOWN"), error);
        } finally {
            waitIfPaused();
        }
    }

    @Override
    public void onBegin(ParseContext parseContext, Parser parser) {
        waitIfPaused();
    }

    @Override
    public void onCommit(ParseContext parseContext, Parser parser, org.unlaxer.TokenList committedTokens) {
        try {
            java.util.List<java.util.Map<String, String>> tokenData = new java.util.ArrayList<>();
            if (committedTokens != null) {
                for (int i = 0; i < committedTokens.size(); i++) {
                    try {
                        org.unlaxer.Token t = committedTokens.get(i);
                        java.util.Map<String, String> m = new java.util.HashMap<>();
                        org.unlaxer.Name name = t.getParser().getName();
                        m.put("name", name != null ? name.toString() : t.getParser().getClass().getSimpleName());
                        m.put("text", t.getSource() != null ? t.getSource().toString() : "");
                        tokenData.add(m);
                    } catch (Exception ignore) {}
                }
            }
            
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("type", "ON_COMMIT");
            event.put("parserId", getParserId(parser));
            event.put("parserType", (parser != null ? parser.getClass().getSimpleName() : "Unknown"));
            event.put("tokens", tokenData);
            if (parseContext != null) {
                event.put("position", parseContext.getConsumedPosition().value());
                event.put("farthest", parseContext.farthestConsumedOffset);
                event.put("station", parseContext.getRemain(TokenKind.consumed).toString());
            }
            if (eventBroadcaster != null) eventBroadcaster.accept(event);

            // Commit is a good time to take a snapshot for the NEXT step.
            parseContext.pushSnapshot();
        } finally {
            waitIfPaused();
        }
    }

    @Override
    public void onRollback(ParseContext parseContext, Parser parser, org.unlaxer.TokenList rollbackedTokens) {
        broadcast("ON_ROLLBACK", parser, parseContext, "ROLLBACK", null);
        waitIfPaused();
    }

    @Override public void setLevel(OutputLevel level) { this.level = level; }
    
    @Override 
    public void onOpen(ParseContext parseContext) {
        // Ensure we have at least one snapshot at the very beginning
        if (parseContext.history.isEmpty()) {
            parseContext.pushSnapshot();
        }
    }
    
    @Override public void onClose(ParseContext parseContext) {}
}
