package com.genealogy.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestDataCleaner testDataCleaner;

    @Autowired
    protected JwtTestHelper jwtTestHelper;

    protected void cleanAllData() {
        testDataCleaner.cleanAll();
    }

    /**
     * Generate a Bearer authorization header value for the given user ID.
     */
    protected String bearerToken(UUID userId) {
        return jwtTestHelper.bearerToken(userId);
    }
}
