

import com.samysadi.acs.core.Simulator;
import com.samysadi.acs.core.event.DispensableEventImpl;
import com.samysadi.acs.core.event.EventImpl;
import com.samysadi.acs.utility.random.Exponential;

public class Job {
	// Ceci est final, vous n'êtes pas censées pouvoir modifier ça après la création
	// du job
	private final long initialDuration;

	/**
	 * La durée complétée (pas forcément Checkpointée)
	 */
	private long completedDuration = 0;

	/**
	 * La durée complétée dans le dernier Checkpoint
	 */
	private long checkpointedDuration = 0;

	/**
	 * Le prochain évènement de Checkpointing
	 */
	private DispensableEventImpl checkEvent = null;

	/**
	 * L'évènement de fin de calcul pour le Job.
	 */
	private EventImpl endEvent;

	/**
	 * Dernier temps du démarrage
	 */
	private long lastTime = 0;

	/**
	 * Le moment où le job a été complété
	 */
	private long completedAt = 0;

	// Event pour redémarrer le job après un checkpointing
	private EventImpl continueJob = null;

	private final CheckpointInterval checkpointIntervalObject;
	private final Exponential deltaGenerator;

	public Job(long initialDuration, CheckpointInterval checkpointIntervalObject, Exponential deltaGenerator) {
		this.initialDuration = initialDuration;
		this.checkpointIntervalObject = checkpointIntervalObject;
		this.deltaGenerator = deltaGenerator;
	}

	public long getInitialDuration() {
		return initialDuration;
	}

	public long getCompletedDuration() {
		return completedDuration;
	}

	private void setCompletedDuration(long completedDuration) {
		this.completedDuration = completedDuration;
	}

	public long getCompletedAt() {
		return completedAt;
	}

	private void setCompletedAt(long completedAt) {
		this.completedAt = completedAt;
	}

	public long getCheckpointedDuration() {
		return checkpointedDuration;
	}

	private void setCheckpointedDuration(long checkpointedDuration) {
		this.checkpointedDuration = checkpointedDuration;
	}

	public DispensableEventImpl getCheckEvent() {
		return checkEvent;
	}

	public long getLastTime() {
		return lastTime;
	}

	private void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}

	public CheckpointInterval getCheckpointIntervalObject() {
		return checkpointIntervalObject;
	}

	public EventImpl createContinueJob() {
		EventImpl e = new EventImpl() {
			@Override
			public void process() {
				// mettre à jour la durée complétée
				setCheckpointedDuration(getCompletedDuration());
				// redémarer le Job
				startJob();
				// mettre l'event continueJob à null
				continueJob = null;
			}
		};
		return e;
	}

	public void startJob() {
		// si le job est déjà en exécution alors l'arrêter avant de le démarrer
		stopJob(); // Cette méthode vérifie si le job est en exécution

		// Calculer le temps restant avant la fin
		long timeToFinish = getInitialDuration() - getCompletedDuration();
		// Si le Job est fini alors quitter
		if (timeToFinish == 0)
			return;

		// Mettre à jour le temps du dernier start pour trouver le temps de calcul
		// effectué
		setLastTime(Simulator.getSimulator().getTime());

		// Planifier l'événement de fin de calcul
		endEvent = new EventImpl() {
			@Override
			public void process() {
				stopJob();
			}
		};
		Simulator.getSimulator().schedule(timeToFinish, endEvent);

		// Si le prochain event de checkpointing existe alors l'annuler
		if (getCheckEvent() != null) {
			getCheckEvent().cancel();
		}
		final long delta = deltaGenerator.nextLong();
		checkEvent = new DispensableEventImpl() {
			@Override
			public void process() {
				// stopper le job
				stopJob();
				// planifier la reprise
				continueJob = createContinueJob();
				Simulator.getSimulator().schedule(delta, continueJob);
			}
		};
		// planifier le checkpointing
		long interval_checkpointing = checkpointIntervalObject.getCheckpointInterval(delta);
		// s'assurer que l'intervalle de Checkpointing n'est pas 0 (sinon, la simulation ne va pas se terminer)
		if (interval_checkpointing == 0)
			interval_checkpointing = 10 * Simulator.MILLISECOND;
		Simulator.getSimulator().schedule(interval_checkpointing, getCheckEvent());
	}

	public void stopJob() {
		// Annuler l'event de Checkpointing
		if (getCheckEvent() != null) {
			getCheckEvent().cancel();
			checkEvent = null;
		}

		// Annuler l'event de reprise
		if (continueJob != null) {
			continueJob.cancel();
			continueJob = null;
		}

		// Si le Job n'a pas d'évent de fin (c-a-d n'est pas en exécution) ou qu'il est
		// déjà fini, alors ne rien faire
		if (endEvent == null || isCompleted())
			return;

		// Annuler l'event de fin
		endEvent.cancel();
		endEvent = null;

		// Mettre à jour la Durée complétée
		setCompletedDuration(getCompletedDuration() + Simulator.getSimulator().getTime() - getLastTime());

		// Si le job est terminé, on note le temps qu'il a pris (on s'en sert après pour
		// calculer le temps moyen)
		if (isCompleted()) {
			setCompletedAt(Simulator.getSimulator().getTime());
		}
	}

	/**
	 * Retourne true si le job est terminé.
	 *
	 * @return true si le job est terminé, false sinon.
	 */
	public boolean isCompleted() {
		return getCompletedDuration() == getInitialDuration();
	}

	/**
	 * Cette méthode récupère l'état du job depuis le Checkpoint.
	 */
	public void recoverFromCheckpoint() {
		// si le job est terminé alors ne rien faire
		if (isCompleted())
			return;

		// si le job est en exécution, alors déclencher une exception (on ne peut pas
		// récupérer l'état d'un job s'il est en exécution)
		if (endEvent != null)
			throw new IllegalStateException(
					"On ne peut pas récuperer l'etat d'un job lorsque celui est toujours en execution!");

		// Le nouveau temps complété = temps complété dans le dernier Checkpoint
		setCompletedDuration(getCheckpointedDuration());
	}
}
