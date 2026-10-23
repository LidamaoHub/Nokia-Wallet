/** No secrets in progress text; cancellation is checked inside long-running loops. */
public interface DerivationProgress {
 void update(String stage,int completed,int total);
 boolean cancelled();
}
