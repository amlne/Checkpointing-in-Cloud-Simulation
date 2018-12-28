

import com.samysadi.acs.core.Simulator;

public class NoCheckpoint implements CheckpointInterval {
	public NoCheckpoint(long mttf) {
		// rien
	}

	@Override
	public long getCheckpointInterval(long delta) {
		return 100 * Simulator.DAY;
	}

	@Override
	public void onFailure(long ttf) {

	}
}
