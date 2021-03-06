package com.maxdemarzi;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.v1.*;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import static org.assertj.core.api.Assertions.*;

public class DistributionTest {

    private static ServerControls neo4j;

    @BeforeAll
    static void startNeo4j() {
        neo4j = TestServerBuilders.newInProcessBuilder()
            .withProcedure(Procedures.class)
            .withFixture(MODEL_STATEMENT)
            .newServer();
    }

    @AfterAll
    static void stopNeo4j() {
        neo4j.close();
    }

    @Test
    void shouldReturnDistribution()
    {
        // In a try-block, to make sure we close the driver after the test
        try( Driver driver = GraphDatabase.driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {

            // Given I've started Neo4j with the procedure
            //       which my 'neo4j' rule above does.
            Session session = driver.session();

            // When I use the procedure
            StatementResult result = session.run( "CALL com.maxdemarzi.fic.distribution");

            // Then I should get what I expect
            assertThat(result.list()).hasSize(2);
        }
    }

    private static final String MODEL_STATEMENT =
            "CREATE (n1:User { username:'User-1' })" +
                    "CREATE (n2:User { username:'User-2' })" +
                    "CREATE (n3:User { username:'User-3' })" +
                    "CREATE (n4:User { username:'User-4' })" +
                    "CREATE (n5:User { username:'User-5' })" +
                    "CREATE (n6:User { username:'User-6' })" +
                    "CREATE (n1)-[:FRIENDS {weight:0.8}]->(n3)" +
                    "CREATE (n2)-[:FRIENDS {weight:0.85}]->(n3)" +
                    "CREATE (n2)-[:FRIENDS {weight:0.7}]->(n1)" +
                    "CREATE (n2)-[:FRIENDS {weight:0.7}]->(n4)" +
                    "CREATE (n3)-[:FRIENDS {weight:0.8}]->(n4)" +
                    "CREATE (n4)-[:FRIENDS {weight:0.9}]->(n5)" +
                    "CREATE (n4)-[:FRIENDS {weight:0.9}]->(n1)" +
                    "CREATE (n4)-[:FRIENDS {weight:0.9}]->(n3)" +
                    "CREATE (n6)-[:FRIENDS {weight:0.8}]->(n4)";
}
