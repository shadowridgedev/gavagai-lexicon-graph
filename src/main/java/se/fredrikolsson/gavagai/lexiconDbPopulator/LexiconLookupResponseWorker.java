package se.fredrikolsson.gavagai.lexiconDbPopulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.UniqueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;


/**
 *
 */
public class LexiconLookupResponseWorker implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(LexiconLookupResponseWorker.class);

    private final BlockingQueue<LookupRequest> lookupRequestQueue;
    private final BlockingQueue<LookupResponse> lookupResponseQueue;
    private final GraphDatabaseService neo4jDb;
    private final Map<String, Integer> seenTermsMap;
    private boolean isRunning;
    private int maxDepth;
    /*
    TODO Two responsibilities
     - create new LookupRequests from the LookupResponse read
       - stop if depth of LookupRequests larger than pre-specified value
     - create Neo4j nodes from the LookupResponse and persistMongoDb them in the db

     */
    // TODO log number of nodes persisted every 30 seconds or so
    // TODO log size of queues at the same time


    public LexiconLookupResponseWorker(
            BlockingQueue<LookupRequest> lookupRequestQueue,
            BlockingQueue<LookupResponse> lookupResponseQueue,
            int maxDepth,
            String dbPath) {

        this.lookupRequestQueue = lookupRequestQueue;
        this.lookupResponseQueue = lookupResponseQueue;
        this.neo4jDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File(dbPath));
        this.seenTermsMap =  new TreeMap<String, Integer>();

        setMaxDepth(maxDepth);
        setRunning(true);
    }

    @Override
    public void run() {
        logger.info("Starting to run");
        while (isRunning()) {
            LookupResponse response = null;
            try {
                response = getLookupResponseQueue().take();
                if (response != null) {
                    createAddRequests(response, getMaxDepth(), getLookupRequestQueue(), getSeenTermsMap());
                    persistNode4j(response);
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted! Aborting processing: {}", e.getMessage(), e);
                setRunning(false);
            } catch (Exception e) {
                logger.error("Caught exception: {}", e.getMessage(), e);
            }
        }
        logger.info("Exiting run method");
    }

    protected void persistNode4j(LookupResponse response) throws JSONException {
        Transaction tx = null;
        try {
            tx = getNeo4jDb().beginTx();

            Node targetTerm = createTargetTermNode(response);

            JSONArray n = response.getPayload().getJSONArray("semanticallySimilarWordFilaments");
            for (int i = 0; i < n.length(); i++) {
                JSONArray labels = n.getJSONObject(i).getJSONArray("labels");
                String semanticLabel = createSemanticLabel(labels);
                JSONArray words = n.getJSONObject(i).getJSONArray("words");
                for (int j = 0; j < words.length(); j++) {
                    Node node = getOrCreateNodeWithUniqueFactory(words.getJSONObject(j).getString("word"), getNeo4jDb());
                    node.addLabel(TermLabel.TERM);

                    Iterable<Relationship> relationships = targetTerm.getRelationships();
                    boolean shouldConnect = true;
                    for (Relationship relationship : relationships) {
                        if (relationship.getOtherNode(targetTerm).equals(node)
                                && relationship.hasProperty("semanticLabel")
                                && relationship.getProperty("semanticLabel").equals(semanticLabel)) {
                            shouldConnect = false;
                            break;
                        }
                    }

                    if (shouldConnect) {
                        Relationship relationship = targetTerm.createRelationshipTo(node, TermRelation.NEIGHBOR);
                        relationship.setProperty("semanticLabel", semanticLabel);
                        relationship.setProperty("strength", words.getJSONObject(j).getDouble("strength"));
                    }
                }

            }


            /*
            // TODO make use of semanticallySimilarWordFilaments instead, to get a hold of semantic labels
            JSONArray neighbors = (response.getPayload()).getJSONArray("semanticallySimilarWords");
            for (int i = 0; i < neighbors.length(); i++) {
                JSONObject neighbor = neighbors.getJSONObject(i);
                Node node = getOrCreateNodeWithUniqueFactory(neighbor.getString("word"), getNeo4jDb());
                node.addLabel(TermLabel.TERM);

                // TODO construct semantic label to use on relationship. Also, qualify the shouldConnect by using the semantic label on the found relation


                Iterable<Relationship> relationships = targetTerm.getRelationships();
                boolean shouldConnect = true;
                for (Relationship relationship : relationships) {
                    if (relationship.getOtherNode(targetTerm).equals(node)) {
                        shouldConnect = false;
                    }
                }
                if (shouldConnect) {
                    Relationship relationship = targetTerm.createRelationshipTo(node, TermRelation.NEIGHBOR);
                    relationship.setProperty("strength", neighbor.getDouble("strength"));
                    // TODO add labelled relation, i.e., the syntagmatic label, as property
                    // TODO add label + strength as additional property, just to use for show


                    // TODO add property for frequency and document frequency
                }
            }
            */
            tx.success();
        } finally {
            if (tx != null) {
                tx.close();
            }
        }
    }


    protected String createSemanticLabel(JSONArray labels) throws JSONException {
        if (labels.length() == 0) {
            return "";
        }
        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();
        for (int i = 0; i < labels.length(); i++) {
            JSONObject label = labels.getJSONObject(i);
            if (label.getString("type").equalsIgnoreCase("LEFT")) {
                left.append(label.getString("label")).append(" | ");
            } else {
                right.append(label.getString("label")).append(" | ");
            }
        }
        if (left.length() > 0) {
            left.delete(left.length() - 3, left.length() - 1);
        }
        if (right.length() > 0) {
            right.delete(right.length() - 3, right.length() - 1);
        }
        left.append(" * ").append(right);
        return left.length() > 3 ? left.toString().trim() : "";
    }

    protected Node createTargetTermNode(LookupResponse response) throws JSONException {
        Node targetTerm = getOrCreateNodeWithUniqueFactory(response.getTargetTerm(), getNeo4jDb());
        targetTerm.addLabel(TermLabel.TERM);
        if (!targetTerm.hasProperty("numTokens")) {
            targetTerm.setProperty("numTokens", computeNumWhitespaces(response.getTargetTerm()) + 1);
        }
        if (!targetTerm.hasProperty("frequency")) {
            targetTerm.setProperty("frequency", response.getFrequency());
        }
        if (!targetTerm.hasProperty("documentFrequency")) {
            targetTerm.setProperty("documentFrequency", response.getDocumentFrequency());
        }
        if (!targetTerm.hasProperty("absoluteRank")) {
            targetTerm.setProperty("absoluteRank", response.getAbsoluteRank());
        }
        if (!targetTerm.hasProperty("relativeRank")) {
            targetTerm.setProperty("relativeRank", response.getRelativeRank());
        }
        return targetTerm;
    }

    protected void createAddRequests(
            LookupResponse response,
            int maxDepth,
            BlockingQueue<LookupRequest> lookupRequestQueue,
            Map<String, Integer> seenTermsMap) throws JSONException {

        if (response.getCurrentDepth() < maxDepth) {
            List<String> terms = response.getSemanticallySimilarTerms();
            for (String term : terms) {
                if (!seenTermsMap.containsKey(term)) {
                    lookupRequestQueue.add(new LookupRequest(term, response.getLanguageCode(), response.getCurrentDepth()));
                    seenTermsMap.put(term, 1);
                } else {
                    Integer c = seenTermsMap.get(term);
                    seenTermsMap.put(term, ++c);
                    //logger.info("Will not create job for \"{}\": already seen the term {} times", term, c);
                }
            }
        } else {
            logger.info("Not spawning new requests. Current depth: {}, max depth: {}", response.getCurrentDepth(), maxDepth);
        }
    }

    protected int computeNumWhitespaces(String input) {
        int numSpaces = 0;
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == ' ') {
                numSpaces++;
            }
            i++;
        }
        return numSpaces;
    }

    public GraphDatabaseService getNeo4jDb() {
        return neo4jDb;
    }

    public BlockingQueue<LookupRequest> getLookupRequestQueue() {
        return lookupRequestQueue;
    }

    public BlockingQueue<LookupResponse> getLookupResponseQueue() {
        return lookupResponseQueue;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Map<String, Integer> getSeenTermsMap() {
        return seenTermsMap;
    }

    private static Node getOrCreateNodeWithUniqueFactory(String nodeName, GraphDatabaseService graphDb) {
            UniqueFactory<Node> factory = new UniqueFactory.UniqueNodeFactory(
                    graphDb, "index") {
                @Override
                protected void initialize(Node created,
                        Map<String, Object> properties) {
                    created.setProperty("name", properties.get("name"));
                }
            };

            return factory.getOrCreate("name", nodeName);
    }

    private enum TermLabel implements Label {
        TERM;
    }

    private enum TermRelation implements RelationshipType {
        NEIGHBOR;
    }

}