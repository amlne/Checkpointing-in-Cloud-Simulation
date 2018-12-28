

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import com.samysadi.acs.core.Simulator;

public class Sortie implements Closeable {
	public final static String SEP = ",";
	public final static String COM = "#";

	private final File summaryFile;
	private BufferedWriter out;

	public Sortie(String filename, String desc) {
		summaryFile = new File(filename);

		out = null;
		try {
			Locale.setDefault(Locale.ENGLISH);
			out = new BufferedWriter(new FileWriter(summaryFile));
			out.write(COM + " Resultats: " + new Date().toString() + "\n");
			out.write(COM + " " + desc + "\n");
			out.write("\n");
			out.write("mttf (heures)" + SEP + "Temps ajoute (%)" + SEP + "nbPannes" + SEP + "jobsRestants" + "\n");

		} catch (IOException e) {
			System.err.println("Cannot create/write summary file!");
			throw new IllegalStateException("Cannot create/write summary file!");
		} finally {
		}
	}

	protected void _writeInfo(long mttf, double pourcentage, int nbPannes, int jobsRestants) throws IOException {
		out.write(String.valueOf(mttf / Simulator.HOUR) + SEP + String.valueOf(Math.round(pourcentage * 100) / 100d)
				+ SEP + String.valueOf(nbPannes) + SEP + String.valueOf(jobsRestants) + "\n");
	}

	public final void writeInfo(long mttf, double pourcentage, int nbPannes, int jobsRestants) {
		try {
			_writeInfo(mttf, pourcentage, nbPannes, jobsRestants);
		} catch (IOException e) {
			System.err.println("Cannot write to summary file!");
			throw new IllegalStateException("Cannot create/write summary file!");
		}
	}

	@Override
	public void close() {
		try {
			if (out != null)
				out.close();
		} catch (IOException e) {
			System.err.println("Cannot close summary file, changes might have not been saved!");
		}
	}

	public String getFileName() {
		try {
			return summaryFile.getCanonicalPath();
		} catch (IOException e) {
			return summaryFile.getAbsolutePath();
		}
	}
}
