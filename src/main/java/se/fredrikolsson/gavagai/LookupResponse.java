package se.fredrikolsson.gavagai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Class holding the information resulting from looking up a term in Gavagai's semantic memories.
 *
 */
class LookupResponse {

    private final JSONObject payload;
    private final int currentDistance;
    private final String languageCode;
    private String targetTerm;


    LookupResponse(JSONObject payload, int currentDistance, String languageCode, String targetTerm) {
        this.payload = payload;
        this.currentDistance = currentDistance;
        this.languageCode = languageCode;
        setTargetTerm(targetTerm);
    }


    JSONObject getPayload() {
        return payload;
    }


    String getLanguageCode() {
        return languageCode;
    }


    int getCurrentDistance() {
        return currentDistance;
    }


    private void setTargetTerm(String targetTerm) {
        this.targetTerm = targetTerm;
    }


    String getTargetTerm() {
        return this.targetTerm;
    }


    private JSONObject getWordInformation() throws JSONException {
        JSONObject result = null;
        if (getPayload() != null && getPayload().getJSONObject("wordInformation") != null) {
            result = getPayload().getJSONObject("wordInformation");
        }
        return result;
    }


    int getFrequency() throws JSONException {
        int result = 0;
        if (getWordInformation() != null) {
            result = getWordInformation().getInt("frequency");
        }
        return result;
    }


    int getDocumentFrequency() throws JSONException {
        int result = 0;
        if (getWordInformation() != null) {
            result = getWordInformation().getInt("documentFrequency");
        }
        return result;
    }


    int getAbsoluteRank() throws JSONException {
        int result = 0;
        if (getWordInformation() != null) {
            result = getWordInformation().getInt("absoluteRank");
        }
        return result;
    }


    double getRelativeRank() throws JSONException {
        double result = 0.0;
        if (getWordInformation() != null) {
            result = getWordInformation().getDouble("relativeRank");
        }
        return result;
    }


    List<String> getSemanticallySimilarTerms() throws JSONException {
        List<String> terms = new ArrayList<>();
        if (getPayload() == null) {
            return terms;
        }
        JSONArray semanticallySimilarWordFilaments = (JSONArray) getPayload().get("semanticallySimilarWordFilaments");
        if (semanticallySimilarWordFilaments == null) {
            return terms;
        }
        for (int i = 0; i < semanticallySimilarWordFilaments.length(); i++) {
            JSONArray words = (JSONArray) ((JSONObject) semanticallySimilarWordFilaments.get(i)).get("words");
            for (int j = 0; j < words.length(); j++) {
                terms.add((String) ((JSONObject) words.get(j)).get("word"));
            }
        }
        return terms;
    }


    public String toString() {
        return getPayload().toString();
    }


}
