package se.fredrikolsson.gavagai;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 */
public class LexiconApiClient {

    private static Logger logger = LoggerFactory.getLogger(LexiconApiClient.class);

    private final String apiKey;
    private static final String LEXICON_API_ENDPOINT = "https://api.gavagai.se/v3/lexicon";

    public static void main(String[] args) throws Exception {
        LexiconApiClient client = new LexiconApiClient("4c775d38fe2d12c43d99858dd0130fa0");
        JSONObject response = client.process("h&m", "sv");
        logger.info("response size {}", response.length());
    }

    LexiconApiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    JSONObject process(String word, String iso639LanguageCode) throws Exception {

        HttpResponse<JsonNode> response = Unirest.get(getLexiconApiEndpoint() + "/{language}/{term}")
                .routeParam("language", iso639LanguageCode)
                .routeParam("term", word)
                .queryString("apiKey", getApiKey())
                .asJson();

        if (response.getStatus() != 200) {
            logger.error("Got HTTP status {}: response body: {}", response.getStatus(), response.getBody().toString());
            throw new Exception("Got HTTP status "
                    + response.getStatus() + ": " + response.getStatusText());
        }
        return response.getBody().getObject();
    }


    private String getApiKey() {
        return apiKey;
    }

    private static String getLexiconApiEndpoint() {
        return LEXICON_API_ENDPOINT;
    }
}