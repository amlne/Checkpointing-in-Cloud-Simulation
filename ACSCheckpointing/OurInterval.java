

import com.samysadi.acs.core.Simulator;
import com.samysadi.acs.core.event.DispensableEventImpl;
import com.samysadi.acs.core.event.Event;

public abstract class OurInterval implements CheckpointInterval {
	protected long ap_Mttf;
	protected Event ajuster;

	public OurInterval(long ap_Mttf) {
		this.ap_Mttf = ap_Mttf;
		this.ajuster = null;
		generateAjuster();
	}

	protected void generateAjuster() {
		if (this.ajuster != null) {
			this.ajuster.cancel();
			this.ajuster = null;
		}
		long delay = ap_Mttf;
		this.ajuster = new DispensableEventImpl() {
			@Override
			public void process() {
				ap_Mttf = ajusterMttf();
			}
		};
		Simulator.getSimulator().schedule(delay, this.ajuster);
	}

	@Override
	public long getCheckpointInterval(long delta) {
		long interval = (long) Math.sqrt(2 * (double) delta * ap_Mttf);
		if (interval <= 0)
			return delta;
		return interval;
	}

	abstract protected long ajusterMttf();
}
