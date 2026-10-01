package uz.bobnoza.wedding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Tools not required: the context must load on machines without ffmpeg/libvips
// (MediaPipelineTest covers the real converters where they exist).
@SpringBootTest(properties = "app.media.require-tools=false")
class WeddingApplicationTests {

	@Test
	void contextLoads() {
	}

}
