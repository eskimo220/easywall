package eskimo220.akidog.easywall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableRetry
@SpringBootApplication
public class EasywallApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasywallApplication.class, args);
	}

}
