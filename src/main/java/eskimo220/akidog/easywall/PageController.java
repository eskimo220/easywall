package eskimo220.akidog.easywall;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.lightsail.LightsailClient;
import software.amazon.awssdk.services.lightsail.model.Domain;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class PageController {

    @Value("classpath:cfn.yml")
    private Resource cfn;

    @Value("classpath:cfn2.yml")
    private Resource cfnWithoutUserData;

    @Value("${PW}")
    private String password;

    @Autowired
    private CfnService cfnService;

    @Autowired
    LogsSseService sseService;

    @ModelAttribute("domain")
    @Cacheable("domain")
    public String getDomainName() {
        return LightsailClient.builder().region(Region.US_EAST_1).build().getDomains().domains().stream().map(Domain::name).findFirst().orElse("");
    }

    @RequestMapping("/")
    public String home(Model model) {

        Region region = Region.AP_NORTHEAST_1;

//        LightsailClient.builder().region(Region.US_EAST_1).build().getDomain(o -> o.domainName(getDomainName())).domain().domainEntries().forEach(System.out::println);

        model.addAttribute("message", CloudFormationClient.builder().region(region).build().describeStacks().stacks()
                .stream().filter(o -> o.tags().stream().anyMatch(tag -> "GFW".equals(tag.key()))).collect(Collectors.toList()));

        return "home";
    }

    @RequestMapping(value = "/", params = "add", method = RequestMethod.POST)
    public String add(Form form, Model model) throws IOException, InterruptedException {

        log.info(form.toString());

        Region region = Region.AP_NORTHEAST_1;

        String id = "f" + form.getUserdata() + IdGen.nextId2();

        Resource resource = !"1".equals(form.getUserdata()) ? cfn : cfnWithoutUserData;

        String cfn2 = StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());

        CreateStackRequest createStackRequest = CreateStackRequest.builder()
                .stackName(id)
                .templateBody(cfn2)
                .parameters(
                        o -> o.parameterKey("LightsailName").parameterValue(id),
                        o -> o.parameterKey("DomainName").parameterValue(getDomainName()),
                        o -> o.parameterKey("Password").parameterValue(password)
                )
                .tags(o -> o.key("GFW").value("GFW").build())
                .build();

        String stackId = CloudFormationClient.builder().region(region).build().createStack(createStackRequest).stackId();

        // add domain entry
        cfnService.addDomainEntry(id, getDomainName());

        model.addAttribute("message", stackId);

        return "redirect:/";
    }

    @RequestMapping(value = "/", params = "delete", method = RequestMethod.POST)
    public String delete(@RequestParam(name = "delete") String delete) throws IOException {

        log.info(delete);

        Region region = Region.AP_NORTHEAST_1;

        CloudFormationClient cloudFormationClient = CloudFormationClient.builder().region(region).build();

        LightsailClient lightsailClient = LightsailClient.builder().region(Region.US_EAST_1).build();

        Domain myDomain = lightsailClient.getDomain(o -> o.domainName(getDomainName())).domain();

        cloudFormationClient.describeStacks().stacks()
                .stream()
                .filter(o -> o.tags().stream().anyMatch(tag -> "GFW".equals(tag.key())))
                .forEach(o -> {
                    if ("*".equals(delete) || delete.equals(o.stackName())) {
                        cloudFormationClient.deleteStack(v -> v.stackName(o.stackName()));
                        myDomain.domainEntries().stream().filter(d -> (o.stackName() + "." + getDomainName()).toLowerCase().equals(d.name())).findFirst().ifPresent(d -> lightsailClient.deleteDomainEntry(domain -> domain.domainName(getDomainName()).domainEntry(d)));

                    }
                });


        return "redirect:/";
    }

    @GetMapping(path = "/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSseMvc() {
        return sseService.newSseEmitter();
    }
}
