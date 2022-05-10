package eskimo220.akidog.easywall;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.lightsail.LightsailClient;

@Service
public class CfnService {

    private final CloudFormationClient cloudFormationClient = CloudFormationClient.builder().region(Region.AP_NORTHEAST_1).build();

    @Async
    @Retryable(maxAttempts = 100, backoff = @Backoff(delay = 5000))
    public void addDomainEntry(String stackName, String domainName) {

        try {
            String ip = cloudFormationClient.describeStacks(o -> o.stackName(stackName)).stacks().get(0).outputs().get(0).outputValue();

            String name = stackName + "." + domainName;

            LightsailClient.builder().region(Region.US_EAST_1).build().createDomainEntry(o -> o.domainName(domainName).domainEntry(entry -> entry.type("A").name(name).target(ip)));

            System.out.println("add domian entry OK");
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("add domian entry NG");
            throw e;
        }
    }
}
