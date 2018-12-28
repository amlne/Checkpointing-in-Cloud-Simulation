

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Random;

import com.samysadi.acs.core.Config;
import com.samysadi.acs.core.Simulator;
import com.samysadi.acs.core.event.DispensableEventImpl;
import com.samysadi.acs.core.event.EventImpl;
import com.samysadi.acs.utility.collections.ShuffledIterator;
import com.samysadi.acs.utility.random.Exponential;

public class MainClass {

	// compteur du nombre de pannes
	private int nbPannes = 0;

	/**
	 * Utilisé comme moyenne lors de la génération des durées initiales pour les
	 * jobs
	 */
	private final long mean_job_length;

	/**
	 * Une collection (liste) contenant tous les jobs.
	 */
	private final ArrayList<Job> jobs;

	/**
	 * Prochain évènement de panne.
	 */
	private DispensableEventImpl breakdownEvent = null;

	/**
	 * Prochain évènement de restart (après une panne).
	 */
	private EventImpl restartEvent = null;

	/**
	 * Utilisé pour générateur de nombres aléatoires principal.
	 */
	private final Random random;

	/**
	 * Utilisé pour générer les pannes.
	 */
	private final Exponential failuresGenerator;

	// moment de la dernière panne
	private long lastFailureTime;

	/**
	 * Utilisé pour générer le "restart delay", ou temps avant qu'un job redémarre
	 * dans une machine secondaire.
	 */
	private final Exponential restartDelayGenerator;

	/**
	 * Mttf réel
	 */
	private final long mttf;

	/**
	 * Mttf à priori initial
	 */
	private final long ap_mttf;

	private final long delta;

	private final Class<? extends CheckpointInterval> checkpointingIntervalClass;

	/**
	 * @param real_mttf          Le MTTF reel (d'un seul job) utilisé pour générer
	 *                           les pannes
	 * @param ap_mttf            Le MTTF à priori (d'un seul job) donné comme
	 *                           paramètre
	 * @param restart_delay_mean Utilisée pour générer le temps avant qu'un job
	 *                           redémarre dans une machine secondaire.
	 * @param job_count          Le nombre de Jobs à lancer
	 * @param mean_job_length    La longueur moyenne d'un job
	 * @param randomSeed         Un nombre utilisé comme graine pour la génération
	 *                           des nombres aléatoires
	 */
	public MainClass(Class<? extends CheckpointInterval> checkpointingIntervalClass, long real_mttf, long ap_mttf,
			long delta, long restart_delay_mean, int job_count, long mean_job_length, long randomSeed) {
		this.checkpointingIntervalClass = checkpointingIntervalClass;
		this.mttf = real_mttf;
		this.ap_mttf = ap_mttf;
		this.delta = delta;
		jobs = new ArrayList<Job>(job_count);
		this.mean_job_length = mean_job_length;
		random = new Random(randomSeed);
		// on crée un nouveau générateur (new random) pour éviter d'interférer avec la
		// génération d'autres nombres
		restartDelayGenerator = new Exponential(restart_delay_mean, new Random(random.nextLong()));
		// on crée un nouveau générateur (new random) pour éviter d'interférer avec la
		// génération d'autres nombres
		// le real_mttf est celui d'un seul job, donc si on a 1000 jobs -> il y a une
		// panne toutes les real_mttf / 1000
		failuresGenerator = new Exponential(real_mttf / job_count, new Random(random.nextLong()));
		lastFailureTime = Simulator.getSimulator().getTime();

		// Initialisation
		generateFailureEvent(0);
		initJobs(job_count);
		startJobs();
	}

	/**
	 * Planifie une panne.
	 * <p>
	 * Cette méthode génère une panne. Puis lorsque la panne survient aucune autre
	 * panne n'est générée jusqu'à ce que le job mis en panne soit redémarré (pour
	 * simplifier le code). Ainsi, si le délai de restart est trop grand (exemple
	 * 10h), alors durant tout ce temps, il n'y a pas de panne qui est générée
	 * (durant 10h donc). Pour remédier à cela, sans trop compliquer le code, il
	 * suffit de passer en paramètre à cette fonction le delai de restart précèdent
	 * qui est ensuite soustrait lors de la planification de l'évènement de panne.
	 * <p>
	 * Ainsi par exemple: s'il y a un job prend 10minutes pour redémarrer, et que la
	 * prochaine devrait être planifiée dans 100h. Alors, elle le sera effectivement
	 * planifiée uniquement à (100h - 10minutes).
	 *
	 * @param lastRestartDelay Délai de redémarrage précèdent (lors de la dernière
	 *                         panne).
	 */
	public void generateFailureEvent(long lastRestartDelay) {
		// si on est entrain d'attendre un restart event, alors on attend
		if (restartEvent != null) {
			return;
		}

		// s'il y a déjà une panne de planifiée, on l'annule
		if (breakdownEvent != null) {
			breakdownEvent.cancel();
			breakdownEvent = null;
		}

		breakdownEvent = new DispensableEventImpl() {
			@Override
			public void process() {
				breakdownEvent = null;

				long ttf = Simulator.getSimulator().getTime() - lastFailureTime;
				lastFailureTime = Simulator.getSimulator().getTime();
				// ajuster le ttf pour chaque job
				ttf = ttf * jobs.size();
				// pour chaque job, appeler sa méthode onFailure()
				for (Job j : jobs)
					j.getCheckpointIntervalObject().onFailure(ttf);

				// seléctionner un job aléatoirement et le mettre en panne (ShuffledIterator est
				// un iterateur offert par ACS pour parcourir une liste aléatoirement)
				ShuffledIterator<Job> iterator = new ShuffledIterator<Job>(jobs, new Random(random.nextLong()));
				final Job job = iterator.next();
				// si le job est terminé, il n'y a rien à faire (on planifie une autre panne)
				if (job.isCompleted()) {
					Simulator.getSimulator().schedule(new EventImpl() {
						@Override
						public void process() {
							generateFailureEvent(0);
						}
					});
				} else {
					nbPannes++;
					// le job doit être mis en panne
					// 1 - arrêter le job
					job.stopJob();
					// 2 - attendre pour un délai = restart délai
					final long restartDelay = restartDelayGenerator.nextLong();
					restartEvent = new EventImpl() {
						@Override
						public void process() {
							restartEvent = null;

							// 3 - récuperer l'etat du job depuis le dernier Checkpoint
							job.recoverFromCheckpoint();

							// 4 - continuer l'execution du job
							job.startJob();

							// 5 - ne pas oublier de planifier un autre évènement de panne
							Simulator.getSimulator().schedule(new EventImpl() {
								@Override
								public void process() {
									generateFailureEvent(restartDelay);
								}
							});
						}
					};
					Simulator.getSimulator().schedule(restartDelay, restartEvent);
				}
			}
		};
		Simulator.getSimulator().schedule(Math.max(0, failuresGenerator.nextLong() - lastRestartDelay), breakdownEvent);
		Simulator.getSimulator().getLogger()
				.log("Une panne a été planifiée à: " + Simulator.formatTime(breakdownEvent.getScheduledAt()));
	}

	/**
	 * Initialise les Jobs avec une durée aléatoire
	 */
	public void initJobs(int jobsCount) {
		// Initialiser le générateur de temps
		Exponential exponential = new Exponential(this.mean_job_length, new Random(random.nextLong()));
		// Initialiser les Jobs
		for (int i = 0; i < jobsCount; i++) {
			Job job = new Job(exponential.nextLong(), createCheckpointInterval(ap_mttf),
					new Exponential(delta, new Random(random.nextLong())));
			jobs.add(job);
		}
		Simulator.getSimulator().getLogger().log("Tous les jobs ont été créés. Nb Jobs = " + jobsCount
				+ ". Durée moyenne (en heures) = " + ((double) this.getAverageJobLength() / Simulator.HOUR));
	}

	/**
	 * Appelle le constructeur de façon dynamique.
	 *
	 * @return Le CheckpointInterval
	 */
	private CheckpointInterval createCheckpointInterval(long mttf) {
		try {
			Constructor<? extends CheckpointInterval> c = checkpointingIntervalClass.getConstructor(long.class);
			return c.newInstance(mttf);
		} catch (Exception e) {
			Simulator.getSimulator().getLogger().log("ERREUR: La classe " + checkpointingIntervalClass.getSimpleName()
					+ " ne contient pas un constructeur adéquat.");
			System.exit(1);
			return null;
		}
	}

	/**
	 * Démarre les Jobs
	 */
	public void startJobs() {
		for (Job job : jobs)
			job.startJob();
		Simulator.getSimulator().getLogger().log("Tous les jobs ont été démarrés");
	}

	public long getAverageCompletionTime() {
		double avg = 0;
		for (Job job : jobs)
			avg += (double) job.getCompletedAt() / jobs.size();
		return (long) avg;
	}

	public long getAverageJobLength() {
		double avg = 0;
		for (Job job : jobs)
			avg += (double) job.getInitialDuration() / jobs.size();
		return (long) avg;
	}

	public Class<? extends CheckpointInterval> getCheckpointingIntervalClass() {
		return checkpointingIntervalClass;
	}

	public int getNbPannes() {
		return nbPannes;
	}

	public long getMttf() {
		return mttf;
	}

	public long getDelta() {
		return delta;
	}

	public int getNumberOfFailures() {
		return nbPannes;
	}

	public static void main(String[] args) {
		final String filename = "resultats";
		// la classe (approche) à utiliser pour le checkpointing
		final Class<? extends CheckpointInterval> APPROCHE = YoungCheckpoint.class;
		OurIntervalMultiplicativeApproach.DEFAULT_A = 1.5;
		OurIntervalAdditiveApproach.DEFAULT_N = 0.5;
		final long DELTA = 5 * Simulator.MINUTE;
		final long RESTART = 5 * Simulator.MINUTE; // delay_restart_moyen
		final int NB_JOBS = 500;
		final long LENGTH_JOB = 1000 * Simulator.HOUR;

		final long[] MTTF_A_TESTER = new long[] { 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 2000, 3000, 4000,
				5000, 6000, 7000, 8000, 9000, 10000, 20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000 };

		// Le MTTF à priori à donner pour l'approche, mettre à null pour donner le MTTF reel
		final Long MTTF_A_PRIORI = 1000000l;

		final Sortie sortie = new Sortie(filename + "." + APPROCHE.getSimpleName() + ".csv",
				"Approche = " + APPROCHE.getSimpleName() + Sortie.SEP + "Delta (moyenne) = "
						+ String.valueOf(DELTA / Simulator.MINUTE) + "min" + Sortie.SEP + "JobLength (moyenne) = "
						+ String.valueOf(LENGTH_JOB / Simulator.HOUR) + "h");

		for (long mttf : MTTF_A_TESTER) {
			final long ap_mttf = MTTF_A_PRIORI == null ? mttf : MTTF_A_PRIORI;

			// Initialiser le Simulateur
			Simulator simulator = new Simulator(new Config());
			MainClass m = new MainClass(APPROCHE,
					// mttf_reel pour chaque job
					mttf * Simulator.HOUR,
					// mttf à priori pour chaque job
					ap_mttf * Simulator.HOUR,
					DELTA, RESTART, NB_JOBS, LENGTH_JOB,
					// randomSeed: il faut que cette valeur soit la meme pour tous
					// les tests pour pouvoir faire une comparaison cohérente
					0);
			// démarre la simulation pour un delai max = taille d'un job * 100
			simulator.start(LENGTH_JOB * 20);
			// affiche le nombre de pannes
			Simulator.getSimulator().getLogger().log("LE NOMBRE DE PANNES = " + m.getNumberOfFailures());
			// affiche le nombre de pannes
			Simulator.getSimulator().getLogger().log("DUREE INITIALE MOYENNE DES JOBS (en heures) = "
					+ ((double) m.getAverageJobLength() / Simulator.HOUR));
			// affiche la durée moyenne de completion d'un job
			Simulator.getSimulator().getLogger().log("LE TEMPS MOYEN POUR COMPLETER LES JOBS (en heures) = "
					+ ((double) m.getAverageCompletionTime() / Simulator.HOUR));
			// affiche le temps ajouté (causé par les pannes / checkpointing)
			long addedTime = m.getAverageCompletionTime() - m.getAverageJobLength();
			Simulator.getSimulator().getLogger()
					.log("LE TEMPS AJOUTÉ (en heures) = " + ((double) addedTime / Simulator.HOUR));
			double pourcentage = (double) addedTime * 100 / m.getAverageJobLength();
			Simulator.getSimulator().getLogger()
					.log("LE TEMPS AJOUTÉ (pourcentage) = " + Math.round(pourcentage * 100) / 100d + "%");

			int jobsRestants = 0;
			for (Job j : m.jobs)
				if (!j.isCompleted())
					jobsRestants++;
			if (jobsRestants != 0)
				Simulator.getSimulator().getLogger()
						.log("LA SIMULATION A ÉTÉ ARRÉTÉ PRÉMATURÉMENT: NbJobs restants = " + jobsRestants);

			sortie.writeInfo(mttf * Simulator.HOUR, pourcentage, m.getNbPannes(), jobsRestants);

			Simulator.getSimulator().free();
		}

		sortie.close();
		System.out.println("Resultats (format csv) enregistrés sous: " + sortie.getFileName());
	}
}
