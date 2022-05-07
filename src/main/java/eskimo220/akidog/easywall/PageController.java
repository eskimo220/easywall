package eskimo220.akidog.easywall;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.lightsail.LightsailClient;
import software.amazon.awssdk.services.lightsail.model.Domain;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

@Controller
public class PageController {

    @Value("classpath:cfn.yml")
    private Resource cfn;

    @Autowired
    private CfnService cfnService;

    @RequestMapping("/")
    public String home(Model model) {

        Region region = Region.AP_NORTHEAST_1;

        System.out.println("region = " + LightsailClient.builder().region(Region.US_EAST_1).build().getDomains().domains());

        model.addAttribute("message", CloudFormationClient.builder().region(region).build().describeStacks().stacks()
                .stream().filter(o -> o.tags().stream().anyMatch(tag -> "GFW".equals(tag.key()))).collect(Collectors.toList()));

        return "home";
    }

    @RequestMapping("/add")
    public String add(Model model) throws IOException {


        Region region = Region.AP_NORTHEAST_1;

        String id = "F" + IdGen.nextId();

        String cfn2 = StreamUtils.copyToString(cfn.getInputStream(), Charset.defaultCharset());

        CreateStackRequest createStackRequest = CreateStackRequest.builder()
                .stackName(id)
                .templateBody(cfn2)
                .parameters(o -> o.parameterKey("LightsailName").parameterValue(id).build())
                .tags(o -> o.key("GFW").value("GFW").build())
                .build();

        String stackId = CloudFormationClient.builder().region(region).build().createStack(createStackRequest).stackId();

        // add domain entry
        cfnService.addDomainEntry(id);

        model.addAttribute("message", stackId);

        return "redirect:/";
    }

    @RequestMapping("/delete-all")
    public String delete() throws IOException {


        Region region = Region.AP_NORTHEAST_1;

        CloudFormationClient cloudFormationClient = CloudFormationClient.builder().region(region).build();

        LightsailClient lightsailClient = LightsailClient.builder().region(Region.US_EAST_1).build();

        Domain myDomain = lightsailClient.getDomain(o -> o.domainName("eskimo.ga")).domain();

        cloudFormationClient.describeStacks().stacks()
                .stream()
                .filter(o -> o.tags().stream().anyMatch(tag -> "GFW".equals(tag.key())))
                .forEach(o -> {
                    cloudFormationClient.deleteStack(v -> v.stackName(o.stackName()));

                    myDomain.domainEntries().stream().filter(d -> (o.stackName() + ".eskimo.ga").toLowerCase().equals(d.name())).findFirst().ifPresent(d -> lightsailClient.deleteDomainEntry(domain -> domain.domainName("eskimo.ga").domainEntry(d)));
                });


        return "redirect:/";
    }
}
