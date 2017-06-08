
import com.fasterxml.jackson.databind.JsonNode;
import com.liveperson.api.Idp;
import java.util.Map;
import java.util.Optional;
import com.liveperson.api.infra.GeneralAPI;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IdpTest {
    public static final String LP_ACCOUNT = System.getenv("LP_ACCOUNT");
    public static final String LP_DOMAINS = "https://" + Optional.ofNullable(System.getenv("LP_DOMAINS"))
            .orElse("adminlogin.liveperson.net");
    private Map<String, String> domains;

    @Before
    public void kk() {
        domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
        Assert.assertTrue(!domains.isEmpty());
    }
    
    @Test
    public void testDomains() throws IOException {
        final JsonNode body = GeneralAPI.apiEndpoint(domains, Idp.class)
                .signup(LP_ACCOUNT)
                .execute().body();
        Assert.assertNotNull(body);
        Assert.assertTrue(!body.path("jwt").asText().isEmpty());
    }

}
