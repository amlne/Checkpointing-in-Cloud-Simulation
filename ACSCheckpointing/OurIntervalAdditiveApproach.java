

public class OurIntervalAdditiveApproach extends OurInterval {
	public static double DEFAULT_N = 0.3;

	private final double n;

	public OurIntervalAdditiveApproach(long ap_Mttf, double n) {
		super(ap_Mttf);
		if (n > 1 || n <= 0)
			throw new IllegalStateException("Le paramètre n doit être compris entre 0 et 1");
		this.n = n;
	}

	public OurIntervalAdditiveApproach(long ap_Mttf) {
		this(ap_Mttf, DEFAULT_N);
	}

	@Override
	public void onFailure(long ttf) {
		ap_Mttf = (long) (ap_Mttf + n * (ttf - ap_Mttf));
		generateAjuster();
	}

	@Override
	protected long ajusterMttf() {
		return (long) (ap_Mttf  + n * ap_Mttf);
	}
}
