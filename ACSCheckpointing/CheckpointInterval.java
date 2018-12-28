

public interface CheckpointInterval {

	long getCheckpointInterval(long delta);

	void onFailure(long ttf);
}
