package eskimo220.akidog.easywall;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.lightsail.LightsailClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

@Service
@Slf4j
public class CfnService {

    @Value("${ansible.path}")
    private String ansiblePath;

    private final CloudFormationClient cloudFormationClient = CloudFormationClient.builder().region(Region.AP_NORTHEAST_1).build();

    @Async
    @Retryable(maxAttempts = 100, backoff = @Backoff(delay = 5000))
    public void addDomainEntry(String stackName, String domainName) throws IOException, InterruptedException {

        try {
            Stack stack = cloudFormationClient.describeStacks(o -> o.stackName(stackName)).stacks().get(0);

            if (stack.stackStatusAsString().startsWith("DELETE")) {
                log.info("is deleted");

                return;
            }

            String ip = stack.outputs().get(0).outputValue();

            String name = stackName + "." + domainName;

            LightsailClient.builder().region(Region.US_EAST_1).build().createDomainEntry(o -> o.domainName(domainName).domainEntry(entry -> entry.type("A").name(name).target(ip)));

            log.info("add domian entry OK");

            if (stackName.charAt(1) == '1') {
                runAnsible(ip);
            }

        } catch (Exception e) {
//            e.printStackTrace();
            log.info("add domian entry NG");

            throw e;
        }
    }

    private void runAnsible(String ip) throws IOException, InterruptedException {
//        -i 54.250.144.215, --private-key ~/Downloads/kaifa.pem -u ec2-user playbook.yml
        ProcessBuilder p = new ProcessBuilder("ansible-playbook", "-i", ip + ",", "--private-key", "kaifa.pem", "-u", "ec2-user", "--ssh-common-args='-o StrictHostKeyChecking=no'", "playbook.yml");

        p.directory(new File(ansiblePath));

        Process process = p.start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.info(line);
            }
        }

        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.error(line);
            }
        }

        int exitVal = process.waitFor();
        if (exitVal == 0) {
            log.info("Command Successfully executed.");
        } else {
            log.info("Error ocured during running command.");
        }

    }
}
