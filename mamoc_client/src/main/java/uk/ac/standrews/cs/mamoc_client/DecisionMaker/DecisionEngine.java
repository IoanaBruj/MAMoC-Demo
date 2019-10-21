package uk.ac.standrews.cs.mamoc_client.DecisionMaker;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import uk.ac.standrews.cs.mamoc_client.Execution.ExecutionLocation;
import uk.ac.standrews.cs.mamoc_client.MamocFramework;
import uk.ac.standrews.cs.mamoc_client.Model.CloudNode;
import uk.ac.standrews.cs.mamoc_client.Model.EdgeNode;
import uk.ac.standrews.cs.mamoc_client.Model.MamocNode;
import uk.ac.standrews.cs.mamoc_client.Model.MobileNode;
import uk.ac.standrews.cs.mamoc_client.Model.TaskExecution;
import uk.ac.standrews.cs.mamoc_client.Profilers.BatteryState;

public class DecisionEngine {

    private static final String TAG = "DecisionEngine";

    private static DecisionEngine instance;
    // TODO: set these values dynamically based on past offloading data and number of checks performed
    private final int MAX_REMOTE_EXECUTIONS = 5;
    private final int MAX_LOCAL_EXECUTIONS = 5;

    private Context mContext;
    private MamocFramework framework;

    private AHP ahp;
    private Topsis topsis;

    private double executionWeight;
    private double energyWeight;

    private DecisionEngine(Context context) {
        this.mContext = context;
        framework = MamocFramework.getInstance(context);
    }

    public static DecisionEngine getInstance(Context context) {
        if (instance == null) {
            synchronized (DecisionEngine.class) {
                if (instance == null) {
                    instance = new DecisionEngine(context);
                }
            }
        }
        return instance;
    }


    public ArrayList<NodeOffloadingPercentage> makeDecision(String taskName, Boolean isParallel) {

        Log.d(TAG, "making offloading decision for: " + taskName);

        ArrayList<MamocNode> sites = getAvailableSites();
        ArrayList<NodeOffloadingPercentage> nodeOffPerc = new ArrayList<>();

        if (executionWeight == 1){
            nodeOffPerc = scorePartitioner(sites);
        }
        else{
            nodeOffPerc = performMCDM(sites);
        }

        int localExecs, remoteExecs;

        ArrayList<TaskExecution> remoteTaskExecutions = framework.dbAdapter.getExecutions(taskName, true);
        remoteExecs = remoteTaskExecutions.size();

        Log.d(TAG, "Remote execs: " + remoteExecs);

        ArrayList<TaskExecution> localTaskExecutions = framework.dbAdapter.getExecutions(taskName, false);
        localExecs = localTaskExecutions.size();

        Log.d(TAG, "Local execs: " + localExecs);
        Log.d(TAG, "Last Execution: " + framework.lastExecution);

        HashMap<MamocNode, ArrayList<Fuzzy>> S = profileAvailableSites();

        // more than 5 local executions of the task - let's check if local is still better
        if (localExecs % MAX_LOCAL_EXECUTIONS == 0){
            Log.d(TAG, "MAX LOCAL EXECUTIONS REACHED");

                return getNodeWithMaxOffloadingScore();
            }
        }

        // more than 5 remote executions of the task - let's recalculate offloading scores
        // and double check if it is still worth offloading
        if (remoteExecs % MAX_REMOTE_EXECUTIONS == 0){
            Log.d(TAG, "MAX REMOTE EXECUTIONS REACHED");

            double localExecTime = 0;
            double remoteExecTime = 0;

            if (localExecs != 0) {
                Log.d(TAG, "The last executed local execution time: " + localTaskExecutions.get(localExecs-1).getExecutionTime());
                localExecTime = localTaskExecutions.get(localExecs-1).getExecutionTime();
            }

            if (remoteExecs != 0) {
                Log.d(TAG, "The last executed remote execution time: " + remoteTaskExecutions.get(remoteExecs - 1).getExecutionTime());
                remoteExecTime = remoteTaskExecutions.get(remoteExecs - 1).getExecutionTime();
            }

            // Compare the last local execution with the last remote execution
            // OR if there is no local Execution at all
            if (localExecTime < remoteExecTime || localExecs == 0){
                framework.lastExecution = "Local";
                return ExecutionLocation.LOCAL;
            } else {
                return getNodeWithMaxOffloadingScore();
            }
        }

        // check if the task has previously been offloaded, if not, let's do some remote offloading
        // to record their execution times
        if (remoteExecs == 0) {
            Log.d(TAG, "No remote executions exist");
            return getNodeWithMaxOffloadingScore();
        }

        // For all other normal remote executions
        if (remoteExecs % MAX_REMOTE_EXECUTIONS != 0 && framework.lastExecution.equals("Remote")) {
            return getNodeWithMaxOffloadingScore();
        }

        // For all other normal local executions
        return ExecutionLocation.LOCAL;
    }


    private ArrayList<MamocNode> getAvailableSites() {

        ArrayList<MamocNode> availableNodes = new ArrayList<>();

        TreeSet<MobileNode> mobileNodes = framework.serviceDiscovery.listMobileNodes();
        TreeSet<EdgeNode> edgeNodes = framework.serviceDiscovery.listEdgeNodes();
        TreeSet<CloudNode> cloudNodes = framework.serviceDiscovery.listPublicNodes();

        availableNodes.addAll(mobileNodes);
        availableNodes.addAll(edgeNodes);
        availableNodes.addAll(cloudNodes);

        return availableNodes;
    }

    private HashMap<MamocNode, ArrayList<Fuzzy>> profileAvailableSites(ArrayList<MamocNode> nodes) {

        HashMap<MamocNode, ArrayList<Fuzzy>> availableSites = new HashMap<>();

        ArrayList<Fuzzy> criteriaImportance;

        for (MamocNode node : nodes) {
//            Log.d(TAG, "Mobile node: " + node.getNodeName() + " " + node.getIp());
            criteriaImportance = profileNode(node);
            availableSites.put(node, criteriaImportance);
        }

        return availableSites;
    }

    private ExecutionLocation getNodeWithMaxOffloadingScore() {

       framework.lastExecution = "Remote";

        // Calculate the weighted decision matrix
        TreeMap<MamocNode, Double> ranking = performEvaluation(availableSites);

        // Simulate a low offloading score node
        MamocNode maxNode = new MamocNode();
        maxNode.setOffloadingScore(0);

        for (Map.Entry<MamocNode,Double> entry: ranking.entrySet()) {
            if (entry.getValue() > maxNode.getOffloadingScore()){
                maxNode =entry.getKey();
            }
        }

        if (maxNode instanceof MobileNode) {
            return ExecutionLocation.D2D;
        } else if (maxNode instanceof EdgeNode) {
            return ExecutionLocation.EDGE;
        } else if (maxNode instanceof CloudNode) {
            return ExecutionLocation.PUBLIC_CLOUD;
        } else {
            // This should not happen
            return ExecutionLocation.LOCAL;
        }

    }

    private ArrayList<NodeOffloadingPercentage> mcdmSolver(ArrayList<MamocNode> availableSites) {
        Log.d(TAG, "Available offloading sites: " + availableSites.size());

        ArrayList<NodeOffloadingPercentage> nodeOffPercList = new ArrayList<>();

        // Calculate the weighted decision matrix
        TreeMap<MamocNode, Double> ranking = performMCDMEvaluation(availableSites);

        for (Map.Entry<MamocNode, Double> entry : ranking.entrySet()) {
            nodeOffPercList.add(new NodeOffloadingPercentage(entry.getKey(), entry.getValue()));
        }

        return nodeOffPercList;
    }

    private ArrayList<Fuzzy> profileNode(MamocNode node) {

        Log.d(TAG, "Profiling " + node.getNodeName() + " - " + node.getIp());

        double cpu, mem, rtt, battery;
        ArrayList<Fuzzy> siteCriteria = new ArrayList<>();

        MobileNode selfNode = framework.getSelfNode();

        // nearby mobile device
        if (node instanceof MobileNode) {

            // calculate RTT values for connected nearby mobile devices
            rtt = framework.networkProfiler.measureRtt(node.getIp(), node.getPort());
            if (rtt < 50) {
                siteCriteria.add(Fuzzy.VERY_HIGH);
            } else if (rtt < 100) {
                siteCriteria.add(Fuzzy.HIGH);
            } else if (rtt < 200) {
                siteCriteria.add(Fuzzy.GOOD);
            } else if (rtt < 300){
                siteCriteria.add(Fuzzy.LOW);
            } else {
                siteCriteria.add(Fuzzy.VERY_LOW);
            }

            // compare cpu and mem of self node and nearby mobile devices
            cpu = framework.deviceProfiler.fetchTotalCpuFreq();
            mem = framework.deviceProfiler.fetchAvailableMemory();

            // Three fold speedup
            if (cpu > (selfNode.getCpuFreq() * 3)  && mem > selfNode.getMemoryMB()) {
                siteCriteria.add(Fuzzy.VERY_HIGH);
            }
            // Two fold speedup
            else if (cpu > (selfNode.getCpuFreq() * 2) && mem > selfNode.getMemoryMB()) {
                siteCriteria.add(Fuzzy.HIGH);
            }
            // Slightly better
            else if (cpu > selfNode.getCpuFreq() && mem > selfNode.getMemoryMB()){
                siteCriteria.add(Fuzzy.GOOD);
            }
            // Worse
            else if (cpu < selfNode.getCpuFreq() && mem < selfNode.getMemoryMB()){
                siteCriteria.add(Fuzzy.VERY_LOW);
            } else {
                siteCriteria.add(Fuzzy.LOW);
            }

            // check the battery level for availability
            BatteryState state = framework.deviceProfiler.isDeviceCharging();
            if (state == BatteryState.CHARGING) {
                battery = 100;
            } else {
                battery = (100 - framework.deviceProfiler.fetchBatteryLevel());
            }

            if (battery > 90) {
                siteCriteria.add(Fuzzy.VERY_HIGH);
            } else if (battery > 80) {
                siteCriteria.add(Fuzzy.HIGH);
            } else if (battery > 50) {
                siteCriteria.add(Fuzzy.GOOD);
            } else if (battery < 20) {
                siteCriteria.add(Fuzzy.VERY_LOW);
            } else {
                siteCriteria.add(Fuzzy.LOW);
            }

            siteCriteria.add(Fuzzy.HIGH); // high security
            siteCriteria.add(Fuzzy.VERY_LOW); // low price
        }
        // edge or public cloud
        else {
            // TODO: get resource monitoring data from the server and set the importance accordingly
            // Edge device
            if (node.getIp().startsWith("192")) { // DIRTY HACK
                siteCriteria.add(Fuzzy.VERY_HIGH); // Bandwidth
                siteCriteria.add(Fuzzy.HIGH);   // Speed
                siteCriteria.add(Fuzzy.HIGH);   // Availability
                siteCriteria.add(Fuzzy.HIGH);   // Security
                siteCriteria.add(Fuzzy.LOW);    // Price
            }
            // Public cloud instance
            else {
                siteCriteria.add(Fuzzy.LOW); // Bandwidth
                siteCriteria.add(Fuzzy.VERY_HIGH); // Speed
                siteCriteria.add(Fuzzy.VERY_HIGH); // Availability
                siteCriteria.add(Fuzzy.GOOD); // Security
                siteCriteria.add(Fuzzy.VERY_HIGH); // Price
            }
        }

        return siteCriteria;
    }

    private TreeMap<MamocNode, Double> performMCDMEvaluation(HashMap<MamocNode, ArrayList<Fuzzy>> availableSites){

        Log.d(TAG, "Calculating AHP Criteria weighting: ");
        AHP ahp = new AHP(Config.criteria);
        ahp.calculateAHP();

        Log.d(TAG, "********************************");
        Log.d(TAG, "Calculating Fuzzy TOPSIS: ");

        topsis = new Topsis();

        return topsis.calculateTopsis(availableSites);
    }
}
