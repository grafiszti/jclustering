package jclustering.techniques;

import static jclustering.GUIUtils.*;
import static jclustering.Utils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import jclustering.Cluster;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

/**
 * This technique implements a <a href="http://en.wikipedia.org/wiki/K-means">
 * k-means clustering algorithm</a>.
 * 
 * @author <a href="mailto:jmmateos@mce.hggm.es">Jos� Mar�a Mateos</a>.
 */

public class KMeans extends ClusteringTechnique implements FocusListener {

    // Default values
    private final int DEF_N_CLUSTERS = 5;
    private final String DEF_INITIAL_CENTROIDS = null;
    private final double DEF_END = 0.0;
    private final int DEF_MAX_ITERATIONS = 100;
    
    // Number of clusters (default = 5).
    private int n_clusters = DEF_N_CLUSTERS;
    // Initial centroids
    private String initial_centroids = DEF_INITIAL_CENTROIDS;
    // End condition
    private double end = DEF_END;
    // Maximum number of iterations
    private int max_iterations = DEF_MAX_ITERATIONS;
    // Non-random initialization
    private String init = "";
    // Image dimensions
    private int[] dim;

    @Override
    public ImagePlus process() {

        IJ.log("K-means clustering started");

        // Initialize points
        int[][] initial_points = new int[n_clusters][3];
        _fillInitialPoints(initial_points);

        // Create container image
        dim = ip.getDimensions();
        ImagePlus res = IJ.createImage("Clusters", "16-bit", dim[0], dim[1],
                dim[3]);
        ImageStack is = res.getStack();

        // Keep track of number of iterations
        int it = 0;

        // When to stop iterating
        boolean threshold_reached = false;

        // Initialize clusters and build string to show which points
        // have been used
        String init = "";
        IJ.log("Initial points used:");
        for (int[] coords : initial_points) {
            IJ.log("   * " + Arrays.toString(coords));
            // Build string
            for (int i = 0; i < coords.length; i++) {
                init += Integer.toString(coords[i]);
                if (i != coords.length - 1)
                    init += ",";
                else
                    init += ";";
            }
            addCluster(ip.getTAC(coords[0], coords[1], coords[2]));
        }
        IJ.log("If you wish to use same initialization, use values below:");
        IJ.log(init);

        // Init this temporary variable
        ArrayList<Cluster> it_result = clusters;

        // Keep iterations below 100 as a sanity measure
        while (!threshold_reached && it < max_iterations) {
            it++;
            IJ.showStatus("K-Means: Iteration " + it + "/" + max_iterations
                    + " , clusters: " + it_result.size());

            ArrayList<Cluster> new_it_result = _iterate(it_result, is);

            // Set it_result for the next iteration. Also, don't create a new
            // cluster if it is empty. Variables size1 and size2 keep track
            // of how many clusters we presently have.
            int size1 = it_result.size();
            it_result = new ArrayList<Cluster>(new_it_result.size());
            for (Cluster c : new_it_result) {
                // Use the previous TACs as centroids
                if (!c.isEmpty()) {
                    it_result.add(new Cluster(c.getClusterTAC()));
                }
            }
            int size2 = it_result.size();

            // Check for differences and set threshold_reached if necessary
            // If there has been some cluster removed, don't check, as it
            // won't be reliable.
            if (size1 == size2) {
                threshold_reached = true;
                for (int i = 0; i < size2; i++) {
                    double[] tac1 = it_result.get(i).getCentroid();
                    double[] tac2 = new_it_result.get(i).getCentroid();
                    threshold_reached &= _compareTACs(tac1, tac2, end);
                }
            }

        }

        IJ.log(it + " iterations needed. " + it_result.size() + " clusters"
                + " formed.");

        // Set final clusters
        clusters = it_result;

        // Return the expanded result
        return expand(res, clusters.size());
    }

    @Override
    public JPanel makeConfig() {

        // Add metrics
        JPanel jp = new JPanel(new GridLayout(5, 2, 5, 5));
        addMetricsToJPanel(jp);

        // Add field for number of clusters to be added
        jp.add(new JLabel("Number of clusters:"));
        JTextField jt_clusters = createJTextField("jt_clusters", n_clusters, 
                                 this);
        jp.add(jt_clusters);

        // Add a field for non-random initialization
        // TODO Implement k-means++ or k-means||
        String non_random_help = "<html>If you wish, you can use this field"
                + " to select those points to be used as initial centroids."
                + "<p>They should be written in the following format:<p>"
                + "<pre>x1,y1,z1;x2,y2,z2</pre><p><p>Example:<p>"
                + "<pre>10,30,40;230,56,39</pre><p><p>"
                + "You don't need to set as many points as clusters. If less"
                + " points are defined, the rest will be randomly chosen as"
                + " usual.</html>";
        jp.add(createJLabel("Non-random initalization*:", non_random_help));
        JTextField jt_init = new JTextField(init);
        jt_init.setName("jt_init");
        jt_init.addFocusListener(this);
        jp.add(jt_init);

        String end_condition_help = "<html>The amount of change between"
                + " clusters below which iterations stop";
        jp.add(createJLabel("Change threshold (%):*", end_condition_help));
        JTextField jt_end = createJTextField("jt_end", end, this);        
        jp.add(jt_end);

        // Add field for number of clusters to be added
        jp.add(new JLabel("Maximum number of iterations:"));        
        JTextField jt_iterations = createJTextField("jt_iterations", 
                                   max_iterations, this);        
        jp.add(jt_iterations);

        return jp;

    }

    @Override
    public void itemStateChanged(ItemEvent arg0) {

        // Handle the metric Choice. Call the superclass method.
        super.itemStateChanged(arg0);

    }

    @Override
    public void focusGained(FocusEvent arg0) {

        Component c = arg0.getComponent();
        String s = c.getName();

        if (s.equals("jt_clusters") || s.equals("jt_init") 
                || s.equals("jt_end") || s.equals("jt_iterations")) {
            ((JTextField) c).selectAll();
        }

    }

    @Override
    public void focusLost(FocusEvent arg0) {

        Component c = arg0.getComponent();
        String source = c.getName();
        if (source.equals("jt_clusters")) {
            JTextField jtf = (JTextField) c;
            try {
                n_clusters = Integer.parseInt(jtf.getText());
            } catch (NumberFormatException e) {
                n_clusters = DEF_N_CLUSTERS;
                jtf.setText(Integer.toString(n_clusters));
            }

        } else if (source.equals("jt_init")) {
            initial_centroids = ((JTextField) c).getText();
        } else if (source.equals("jt_end")) {
            JTextField jtf = (JTextField) c;
            try {
                end = Double.parseDouble(((JTextField) c).getText());
            } catch (NumberFormatException e) {
                end = DEF_END;
                jtf.setText(Double.toString(end));
            }
        } else if (source.equals("jt_iterations")) {
            JTextField jtf = (JTextField) c;
            try {
                max_iterations = Integer.parseInt(jtf.getText());
            } catch (NumberFormatException e) {
                max_iterations = DEF_MAX_ITERATIONS;
                jtf.setText(Integer.toString(max_iterations));
            }

        }

    }

    /**
     * Fills the {@code initial_points} array using the
     * {@code initial_centroids} String.
     * 
     * @param initial_points Array to be filled
     */
    private void _fillInitialPoints(int[][] initial_points) {

        if (initial_centroids == null || initial_centroids.equals("")
                || _notValidInitialPoints()) {
            _fillRandomPoints(initial_points, 0);
            return;
        }

        String[] point_triplets = initial_centroids.split(";");
        for (int i = 0; i < point_triplets.length; i++) {

            String[] coordinates = point_triplets[i].split(",");

            // Now fill with the values
            try {
                initial_points[i][0] = Integer.parseInt(coordinates[0]);
                initial_points[i][1] = Integer.parseInt(coordinates[1]);
                initial_points[i][2] = Integer.parseInt(coordinates[2]);
            } catch (NumberFormatException ex) {
                // Do nothing, this has already been checked
                // in the _notValidInitialPoints() method. This is
                // caught here because this method does not throw
                // any exceptions.
            }
        }

        // If there is any point left to be filled, do so randomly
        if (point_triplets.length != n_clusters) {
            _fillRandomPoints(initial_points, point_triplets.length);
        }

    }

    /**
     * Randomly fills the {@code initial_points} array starting from a certain
     * offset.
     * 
     * @param initial_points Array to be filled
     * @param start Initial offset
     */
    private void _fillRandomPoints(int[][] initial_points, int start) {

        Random r = new Random(System.currentTimeMillis());
        int[] dim = ip.getDimensions(); // 0 -> x; 1 -> y; 3 -> z

        for (int i = start; i < n_clusters; i++) {
            // FIXME Check whether that coordinate
            // - has not been selected yet.
            do {
                initial_points[i][0] = r.nextInt(dim[0]);
                initial_points[i][1] = r.nextInt(dim[1]);
                initial_points[i][2] = r.nextInt(dim[3]) + 1;
            } while (skip_noisy
                    && isNoise(ip.getTAC(initial_points[i][0],
                            initial_points[i][1], initial_points[i][2])));

        }
    }

    /**
     * Performs a quick check of the {@code initial_centroids} String to make
     * sure the syntax is correct
     * 
     * @return {@code true} if the {@code initial_centroids} String is not
     *         valid.
     */
    private boolean _notValidInitialPoints() {

        boolean correct = false;

        // Trim initial_centroids
        initial_centroids = initial_centroids.trim();

        // If initial_centroids ends in a semicolon, remove it,
        // it is not necessary.
        if (initial_centroids.endsWith(";")) {
            initial_centroids = initial_centroids.substring(0,
                    initial_centroids.length() - 2);
        }

        // If initial_centroids ends in a comma, end. Badly formed string
        if (initial_centroids.endsWith(","))
            return !correct;

        // Count number of commas and semicolons. Number of commas must be even
        // and number of commas and semicolons must observe a certain ratio
        int comma_count = initial_centroids.split(",").length - 1;
        int sc_count = initial_centroids.split(";").length - 1;
        if (comma_count % 2 != 0 || comma_count != sc_count * 2 + 2)
            return !correct;

        // Check for out of bounds parameters and characters that are not
        // digits
        String[] test_digits = initial_centroids.split(";");
        int[] dim = ip.getDimensions(); // 0 -> x; 1 -> y; 3 -> slice

        for (String s : test_digits) {
            String[] coordinates = s.split(",");
            try {
                if (Integer.parseInt(coordinates[0]) >= dim[0]
                        || Integer.parseInt(coordinates[1]) >= dim[1]
                        || Integer.parseInt(coordinates[2]) >= dim[3])
                    return !correct;
            } catch (NumberFormatException ex) {
                // Some element is not a digit
                return !correct;
            }
        }

        // No error found? Ok, it is correct then
        return correct;

    }

    /*
     * Performs an iteration of the algorithm and creates a new ArrayList of
     * Cluster objects that is returned. Also sets the corresponding voxels in
     * the {@link ImageStack}.
     */
    private ArrayList<Cluster> _iterate(ArrayList<Cluster> models, 
            ImageStack is) {

        // Create a result ArrayList and initialize the original centroids
        // to the previous TACs
        int size = models.size();
        ArrayList<Cluster> res = new ArrayList<Cluster>(size);
        for (int i = 0; i < size; i++) {
            res.add(new Cluster(models.get(i).getCentroid()));
        }

        // Get each TAC, compare and add it to the closest cluster
        for (int slice = 1; slice <= dim[3]; slice++) {
            for (int x = 0; x < dim[0]; x++) {
                for (int y = 0; y < dim[1]; y++) {

                    double[] tac = ip.getTAC(x, y, slice);

                    // If is noise, skip
                    if (skip_noisy && isNoise(tac))
                        continue;

                    int cluster_index = _getClosestCluster(tac, res);

                    // Set results.
                    res.get(cluster_index).add(tac);
                    // Visual value is 1-based.
                    setVoxel(is, x, y, slice, cluster_index + 1);

                }
            }
        }

        return res;
    }

    /*
     * Returns the cluster index which is closest to the given tac.
     */
    private int _getClosestCluster(double[] tac, ArrayList<Cluster> l) {

        int index = -1;
        int size = l.size();
        double d = Double.MAX_VALUE;

        for (int i = 0; i < size; i++) {
            double[] centroid = l.get(i).getCentroid();
            double temp = metric.distance(tac, centroid);
            if (temp < d) {
                d = temp;
                index = i;
            }
        }

        return index;

    }

    /*
     * Returns whether tac1 and tac2 are the same within certain sensitivity
     * limits given by the "end" variable.
     */
    private boolean _compareTACs(double[] tac1, double[] tac2, double end) {

        // Convert percentage to double value.
        end /= 100;

        // If no sensitivity value set (0.0, compare with 0.001)
        // use Arrays.equals.
        if (end < 0.001) {
            return Arrays.equals(tac1, tac2);
        } else {
            for (int i = 0; i < tac1.length; i++) {
                double t = 1.0 - end; // Sensitivity threshold
                double ratio = tac1[i] > tac2[i] ? tac2[i] / tac1[i] : 
                               tac1[i] / tac2[i];
                // If ratio is negative or below threshold, return false
                // else, let loop finish and return true at the end.
                if (ratio < 0 || ratio < t)
                    return false;
            }
        }

        return true;

    }

}