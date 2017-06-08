import java.util.Map;
import java.util.Optional;
import com.liveperson.api.infra.GeneralAPI;
import org.junit.Assert;
import org.junit.Test;


public class DomainsTest {
    public static final String LP_ACCOUNT = System.getenv("LP_ACCOUNT");
    public static final String LP_DOMAINS = "https://" + Optional.ofNullable(System.getenv("LP_DOMAINS"))
            .orElse("adminlogin.liveperson.net");
    
    @Test
    public void testDomains() {
        Map<String, String> domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
        Assert.assertTrue(!domains.isEmpty());        
    }

}
