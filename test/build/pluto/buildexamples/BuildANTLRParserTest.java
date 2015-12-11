package build.pluto.buildexamples;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;

import org.junit.Test;

import build.pluto.builder.BuildManagers;
import build.pluto.builder.BuildRequest;

public class BuildANTLRParserTest {
  @Test
  public void testBuildANTLRParserForJava() throws Throwable {
    String language = "Java";
    URL grammarLocation = new URL("https://raw.githubusercontent.com/antlr/grammars-v4/master/java/Java.g4");
    BuildANTLRParser.Input input = new BuildANTLRParser.Input(language, grammarLocation, "parser.java", null, null);
    File jarFile = BuildManagers.build(new BuildRequest<>(BuildANTLRParser.factory, input)).val();
    assertTrue(jarFile.exists());
  }
}
