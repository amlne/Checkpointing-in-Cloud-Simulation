

public class YoungCheckpoint implements CheckpointInterval {
	private final long mttf;

	public YoungCheckpoint(long mttf) {
		this.mttf = mttf;
	}

	@Override
	public long getCheckpointInterval(long delta) {
		return (long) Math.sqrt(2 * (double) delta * mttf);
	}

	@Override
	public void onFailure(long ttf) {

	}
}
