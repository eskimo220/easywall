package eskimo220.akidog.easywall;

import org.springframework.data.domain.Example;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.lightsail.LightsailClient;

@Service
public class CfnService {

    @Async
    @Retryable(maxAttempts = 100, backoff = @Backoff(delay = 5000))
    public void addDomainEntry(String stackName) {

        try {
            Region region = Region.AP_NORTHEAST_1;

            String ip = CloudFormationClient.builder().region(region).build().describeStacks(o -> o.stackName(stackName)).stacks().get(0).outputs().get(0).outputValue();

            LightsailClient.builder().region(Region.US_EAST_1).build().createDomainEntry(o -> o.domainName("eskimo.ga").domainEntry(entry -> entry.type("A").name(stackName + ".eskimo.ga").target(ip)));

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
