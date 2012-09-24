package jclustering.techniques;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import jclustering.Cluster;
import jclustering.MathUtils;
import jclustering.Voxel;
import static jclustering.GUIUtils.*;

import ij.IJ;

/**
 * Implements a leader-follower clustering method using only correlation
 * as its main metric. Also uses the mean peak value of all the TACs inside
 * a Cluster to decide whether a certain TAC belongs or not to a voxel
 * with similar dynamic shape. 
 * <p>
 * Leader-follower works in an inverse way as K-means does. The number of
 * clusters is unknown, and a threshold is set so that clusters are formed
 * with TACs with a distance (correlation, in this case) that evaluates
 * above said threshold. An optional increment value can be set so that
 * every time a voxel is added to a cluster this becomes more restrictive.
 * <p>
 * Also, peak amplitude is taken into account when adding new TACs to an 
 * existing cluster.
 * 
 * @author <a href="mailto:jmmateos@mce.hggm.es">Jos� Mar�a Mateos</a>.
 */
public class LeaderFollower extends ClusteringTechnique 
    implements FocusListener {

    // Default values
    private final int DEF_MAX_CLUSTERS = 1000;
    private final boolean DEF_DISCARD_SMALLEST = false;
    private final int DEF_KEEP_CLUSTERS = 50;
    private final double DEF_THRESHOLD = 0.3;
    private final double DEF_T_INC = 1.0;
    
    // Maximum number of clusters to compute
    private int max_clusters = DEF_MAX_CLUSTERS;
    
    // Discard smallest cluster when max_clusters is reached
    private boolean discard_smallest = DEF_DISCARD_SMALLEST;
    
    // Maximum number of clusters to keep
    private int keep_clusters = DEF_KEEP_CLUSTERS;
    
    // Threshold for cluster addition
    private double threshold = DEF_THRESHOLD;
    
    // Threshold increment
    private double t_inc = DEF_T_INC;
    private Hashtable<Cluster, Double> corr_limits;

    // Pearson Correlation object
    private PearsonsCorrelation pc;
    
    // Need to keep track of which coordinates belong to which cluster
    private Hashtable<Cluster, ArrayList<Integer[]>> record;     
    
    // Cluster comparator
    private Comparator<Cluster> comp;
    
    @Override
    public void process() {
        
        /*
         * Initial object creation.
         */                
        
        pc = new PearsonsCorrelation();
        
        record = new Hashtable<Cluster, ArrayList<Integer[]>>();
        corr_limits = new Hashtable<Cluster, Double>();
        
        comp = new LeaderFollowerClusterComparator();
        
        IJ.log(String.format("Correlation limit: %f; increment: %f.",
        		threshold, t_inc));
        
        /*
         * Process all TACs               
         */
        int slice = 0;
        
        for (Voxel v : ip) {

            if (slice != v.slice) {
                // Show status message for every slice
                String status = String.format("Leader-follower. Slice %d, " +
            		"%d/%d clusters", slice, clusters.size(), max_clusters);
                IJ.showStatus(status);
                // Update slice pointer
                slice = v.slice;
            }
                
            // If is noise, skip
            if (skip_noisy && isNoise(v)) continue;

            int size = clusters.size();

            // Is it the first voxel? If so, just put it in a
            // new cluster
            if (clusters.isEmpty()) {
                Cluster c = new Cluster(v);
                clusters.add(c);
                _addToRecord(c, v.x, v.y, v.slice);
            }
            // Are we within the max_clusters limit or can we get rid
            // of any of them?
            else if (size < max_clusters || discard_smallest) {

                // If too many clusters, throw away the smallest of
                // them, if allowed by the settings.
                if (size == max_clusters && discard_smallest) {
                    _discardSmallest();
                }

                // Get closest cluster
                int cindex = _getClosestCluster(v.tac);

                if (cindex >= 0) {
                    // There is a cluster that can include this voxel
                    Cluster c = clusters.get(cindex);
                    // Add TAC modifying centroid
                    c.add(v);
                    _addToRecord(c, v.x, v.y, v.slice);
                } else {
                    // There is no cluster to include this voxel yet
                    Cluster c = new Cluster(v);
                    clusters.add(c);
                    _addToRecord(c, v.x, v.y, v.slice);
                }
            }
        }
                
        
        /*
         * Get rid of the smallest clusters
         */
        
        // Sort clusters
        Collections.sort(clusters, comp);
        
        // Build new list for inserting the selected clusters
        ArrayList<Cluster> good_clusters = new ArrayList<Cluster>();
        
        // Get last "keep_clusters" and set the corresponding voxel in the
        // ImageStack is
        int size = clusters.size();
        
        // Can't keep more clusters that the ones that have been created
        if (keep_clusters > size)
            keep_clusters = size;
        
        // Go backwards: biggest cluster is #1
        for (int i = size - 1; i >= size - keep_clusters; i--) {            
            Cluster c = clusters.get(i);
            good_clusters.add(c);
        }
        
        // Change reference
        clusters = good_clusters;
        
        /*
         * Log result and return 
         */
                
        int nsize = clusters.size();
        IJ.log(String.format("Leader-follower finished. %d clusters " +
        		"created, %d kept.", size, nsize));
    }
    
    public JPanel makeConfig() {
        
        JPanel jp = new JPanel(new GridLayout(5, 2, 5, 5));
        
        // Maximum number of clusters
        jp.add(new JLabel("Maximum clusters to form:"));
        JTextField jt_maxclust = createJTextField("jt_maxclust", max_clusters, 
                                 this);
        jp.add(jt_maxclust);
        
        // Discard smallest cluster
        String discard_help = "<html>If a new cluster needs to be created<br>" +
        		"but the maximum permitted number has been reached,<br>" +
        		"discard the smallest one (the one containing less voxels<br>" +
        		"with lowest amplitudes).</html>";
        jp.add(createJLabel("Discard smallest cluster:*", discard_help));
        JCheckBox jcb_discard = new JCheckBox();
        jcb_discard.setSelected(discard_smallest);
        jcb_discard.setName("jcb_discard");
        jcb_discard.addItemListener(this);
        jp.add(jcb_discard);
        
        // Number of clusters to keep
        String keep_clusters_help = "<html>If more than these clusters<br>" +
        		"are formed, the cluster list will be ordered (biggest<br>" +
        		"clusters first) and the ones exceeding this variable<br>" +
        		"will be discarded.</html>";
        jp.add(createJLabel("Clusters to keep:*", keep_clusters_help));
        JTextField jt_keepclust = createJTextField("jt_keepclust", 
                                  keep_clusters, this);
        jp.add(jt_keepclust);
        
        // Initial correlation threshold
        jp.add(new JLabel("Initial correlation threshold:"));
        JTextField jt_thres = createJTextField("jt_thres", threshold, this);
        jp.add(jt_thres);
        
        // Correlation increment
        jp.add(new JLabel("Correlation increment:"));
        JTextField jt_inc = createJTextField("jt_inc", t_inc, this);
        jp.add(jt_inc);
        
        return jp;
        
    }
    
    /*
     * Get closest cluster to provided TAC.
     */
    private int _getClosestCluster(double [] tac) {
        
        int i = -1;
        double max_score = -Double.MAX_VALUE;
        int size = clusters.size();
        
        for (int j = 0; j < size; j++) {
            
            Cluster c = clusters.get(j);
            
            // Smooth the TAC only for correlation computing purposes, do
            // not use it afterwards.
            double score = pc.correlation(MathUtils.smooth(tac), 
                    c.getCentroid());
            
            // Get correlation value for that cluster
            Double d = corr_limits.get(c);
            double threshold = d != null ? d : this.threshold;
            
            // Compute peak value for this TAC and obtain peak threshold
            // for current cluster.
            double peak = MathUtils.getMax(tac);
            double peak_threshold = c.getPeakMean() - c.getPeakStdev();
            
            if (score > threshold && score > max_score && 
                    peak >= peak_threshold) {                
                max_score = score;
                i = j;                
            }            
        }        
        
        return i;
        
    }
    
    /*
     * Keeps track of which voxels have been added to which cluster and
     * increments the correlation limit.
     */
    private void _addToRecord(Cluster c, int x, int y, int slice) {
        
        Integer [] coordinates = new Integer[] {x, y, slice};
        ArrayList<Integer []> ints = record.get(c);
        
        // Use Double instead of double so the comparison cor != null
        // can be made.
        Double cor = corr_limits.get(c);
        // Compute updated score or use original threshold if no prior
        // score is found.
        double score = (cor != null) ? cor * t_inc : threshold;        
        
        // If the cluster has not been yet added, create it and add to record
        if (ints == null) {
            ints = new ArrayList<Integer[]>();
            record.put(c, ints);            
        }
        
        // Update the score and add the coordinates to record.
        corr_limits.put(c, score);
        ints.add(coordinates);
        
    }
    
    /*
     * Discard smallest cluster. Uses the private Comparator.
     */    
    private void _discardSmallest() {

        Cluster min = Collections.min(clusters, comp);       
        clusters.remove(min);
        corr_limits.remove(min);
        
    }

    @Override
    public void focusGained(FocusEvent arg0) {
        
        Component c = arg0.getComponent();
        String s = c.getName();
        
        // Just select all the text in the JTextField, for usability.
        if (s.equals("jt_maxclust") || s.equals("jt_keepclust")
                || s.equals("jt_thres") || s.equals("jt_inc")) {
            ((JTextField)c).selectAll();
        }
        
    }

    @Override
    public void focusLost(FocusEvent arg0) {

        Component c = arg0.getComponent();
        String s = c.getName();
        
        // Just set each value to the corresponding JTextField
        if (s.equals("jt_maxclust")) {
            try {                
                max_clusters = Integer.parseInt(((JTextField)c).getText());
            } catch (NumberFormatException e) {
                max_clusters = DEF_MAX_CLUSTERS;                
            }
            ((JTextField)c).setText(Integer.toString(max_clusters));  
            
        } else if (s.equals("jt_keepclust")) {
            try {                
                keep_clusters = Integer.parseInt(((JTextField)c).getText());
            } catch (NumberFormatException e) {
                keep_clusters = DEF_KEEP_CLUSTERS;                
            }
            ((JTextField)c).setText(Integer.toString(keep_clusters));
            
        } else if (s.equals("jt_thres")) {
            try {                
                threshold = Double.parseDouble(((JTextField)c).getText());
            } catch (NumberFormatException e) {
                threshold = DEF_THRESHOLD;                
            }
            ((JTextField)c).setText(Double.toString(threshold));
            
        } else if (s.equals("jt_inc")) {
            try {                
                t_inc = Double.parseDouble(((JTextField)c).getText());
            } catch (NumberFormatException e) {
                t_inc = DEF_T_INC;                
            }
            ((JTextField)c).setText(Double.toString(t_inc));
        }        
    }
    
    
    public void itemStateChanged(ItemEvent arg0) {
                        
        // Check the checkbox for discard_smallest
        JCheckBox jcb = (JCheckBox)arg0.getSource();
        discard_smallest = jcb.isSelected();
        
    }
    
    /*
     * Use this comparator class to compare Clusters between them.
     */
    private class LeaderFollowerClusterComparator 
        implements Comparator<Cluster> {

        @Override
        public int compare(Cluster arg0, Cluster arg1) {
            double score0 = arg0.getPeakMean() * arg0.size();
            double score1 = arg1.getPeakMean() * arg1.size();
            if (score0 < score1) return -1;
            else if (score0 > score1) return 1;
            else return 0;
        }
        
    }

}
