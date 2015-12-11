package build.pluto.buildexamples;

import java.net.URL;

import build.pluto.builder.BuildManagers;
import build.pluto.builder.BuildRequest;

public class Main {

  public static void main(String[] args) throws Throwable {
    
    String language = args[0];
    URL grammarLocation = new URL(args[1]);
    
    BuildANTLRParser.Input input = new BuildANTLRParser.Input(language, grammarLocation, "parser", null, null);
    BuildManagers.build(new BuildRequest<>(BuildANTLRParser.factory, input));
  }

}
