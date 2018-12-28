

import com.samysadi.acs.core.Simulator;

public class OurIntervalMultiplicativeApproach extends OurInterval {
	public static double DEFAULT_A = 1.1;

	private final double a;

	public OurIntervalMultiplicativeApproach(long ap_Mttf, double a) {
		super(ap_Mttf);
		if (a <= 1)
			throw new IllegalStateException("Le paramètre a doit être strictement supérieur à 1");
		this.a = a;
	}

	public OurIntervalMultiplicativeApproach(long ap_Mttf) {
		this(ap_Mttf, DEFAULT_A);
	}

	private long verifierDepassement(long v) {
		// s'il y a dépassement arithmétique, ou si on dépasse la capacité du simulateur (temps maximal)
		if (v < 0 || v >= Simulator.getSimulator().getMaximumScheduleDelay())
			v = Simulator.getSimulator().getMaximumScheduleDelay();
		return v;
	}

	@Override
	public void onFailure(long ttf) {
		ap_Mttf = (long) (ap_Mttf * Math.pow(a, (double) (ttf - ap_Mttf) / ap_Mttf));
		ap_Mttf = verifierDepassement(ap_Mttf);
		generateAjuster();
	}

	@Override
	protected long ajusterMttf() {
		long v = (long) (ap_Mttf  * a);
		v = verifierDepassement(v);
		return v;
	}
}
