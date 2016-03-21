package build.pluto.buildexamples;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.sugarj.common.FileCommands;

import build.pluto.builder.Builder;
import build.pluto.builder.factory.BuilderFactory;
import build.pluto.builder.factory.BuilderFactoryFactory;
import build.pluto.buildhttp.HTTPDownloader;
import build.pluto.buildhttp.HTTPInput;
import build.pluto.buildjava.JavaBulkCompiler;
import build.pluto.buildjava.JavaCompilerInput;
import build.pluto.buildjava.JavaJar;
import build.pluto.buildjava.JavaRunner;
import build.pluto.buildjava.JavaRunnerInput;
import build.pluto.buildmaven.MavenDependencyResolver;
import build.pluto.buildmaven.input.ArtifactConstraint;
import build.pluto.buildmaven.input.Dependency;
import build.pluto.buildmaven.input.MavenInput;
import build.pluto.dependency.Origin;
import build.pluto.output.Out;
import build.pluto.output.OutputPersisted;

/**
 * Builds an ANTLR parser for a given a language in a given version.
 * Yields the location of the generated jar file.
 * 
 * @author seba
 *
 */
public class BuildANTLRParser extends Builder<BuildANTLRParser.Input, Out<File>> {

  public static class Input implements Serializable {

    private static final long serialVersionUID = -6164439592720139236L;
    
    public final String language;
    public final URL grammarLocation;
    public final String parserPackage;
    public final File targetDir;
    public final File targetJar;

    /**
     * @param language Name of the parsed language.
     * @param grammarLocation URL to the directory containing the grammars (optional).
     * @param parserPackage Name of the Java package for the parser.
     * @param targetDir Directory to generate the class files of the parser in (defaults to a fresh temporary directory).
     * @param targetJar Location to generate the jar file of the parser at (defaults to a fresh temporary file).
     */
    public Input(String language, URL grammarLocation, String parserPackage, File targetDir, File targetJar) {
      this.language = language;
      this.grammarLocation = grammarLocation;
      this.parserPackage = parserPackage == null ? "" : parserPackage;
      this.targetJar = targetJar;
      this.targetDir = targetDir;
    }
  }
  
  public BuildANTLRParser(Input input) {
    super(input);
  }
  
  public static BuilderFactory<Input, Out<File>, BuildANTLRParser> factory = BuilderFactoryFactory.of(BuildANTLRParser.class, Input.class);
 
  @Override
  protected String description(Input input) {
    return "Compile ANTLR parser for " + input.language;
  }
  
  @Override
  public File persistentPath(Input input) {
    return new File(PLUTO_HOME, "antlr/" + input.language + input.grammarLocation.hashCode() + ".dep");
  }
  
  @Override
  protected Out<File> build(Input input) throws Throwable {
    File tempDir = FileCommands.newTempDir();
    File targetDir = input.targetDir != null ? input.targetDir : new File(tempDir, "bin");
    File targetJar = input.targetJar != null ? input.targetJar : new File(tempDir, "parse-" + input.language + ".jar");
    
    // 1) Fetch antlr binaries
    Dependency antlrDep = new Dependency(
        new ArtifactConstraint("org.antlr", "antlr4", "[4.0,)", null, null), 
        TimeUnit.DAYS.toMillis(1)); // check for an update of the grammar at most once per day
    MavenInput mavenInput = new MavenInput
        .Builder()
        .addDependency(antlrDep)
        .build();
    List<File> mavenJars = requireBuild(MavenDependencyResolver.factory, mavenInput).val();
    Origin jarOrigin = Origin.from(lastBuildReq());
    
    // 2) Fetch grammar
    File grammarFile = new File(tempDir, input.grammarLocation.getFile());
    HTTPInput httpInput = new HTTPInput(
        input.grammarLocation.toString(), 
        grammarFile, 
        TimeUnit.HOURS.toMillis(1)); // check for an update of the grammar at most once per hour
    requireBuild(HTTPDownloader.factory, httpInput);
    
    // 3) Compile grammar to Java and to class files
    File sourceDir = new File(tempDir, "src");
    File parserSourceDir;
    if (input.parserPackage.isEmpty())
      parserSourceDir = sourceDir;
    else
      parserSourceDir = new File(sourceDir, input.parserPackage.replace('.', File.separatorChar));
    FileCommands.createDir(parserSourceDir);
    
    JavaRunnerInput runnerInput = JavaRunnerInput
        .Builder()
        .setWorkingDir(sourceDir)
        .addClassPaths(mavenJars)
        .setClassOrigin(jarOrigin)
        .setMainClass("org.antlr.v4.Tool")
        .addProgramArgs("-o", parserSourceDir.toString())
        .addProgramArgs("-package", input.parserPackage)
        .addProgramArgs(grammarFile.toString())
        .get();
    requireBuild(JavaRunner.factory, runnerInput);
    Origin sourceOrigin = Origin.from(lastBuildReq());

    List<File> sourceFiles = FileCommands.listFilesRecursive(sourceDir, new SuffixFileFilter(".java"));
    JavaCompilerInput compilerInput = JavaCompilerInput
        .Builder()
        .addInputFiles(sourceFiles)
        .addClassPaths(mavenJars)
        .setClassOrigin(jarOrigin)
        .addSourcePaths(sourceDir)
        .setSourceOrigin(sourceOrigin)
        .setTargetDir(targetDir)
        .get();
    requireBuild(JavaBulkCompiler.factory, compilerInput);
    Origin classFileOrigin = Origin.from(lastBuildReq());
    
    // 4) Package classes into jar file
    Set<File> files = new HashSet<>(FileCommands.listFilesRecursive(targetDir, new SuffixFileFilter(".class")));
    if (files.isEmpty())
      throw new IllegalStateException("Could not find any class files in " + targetDir);
    
    JavaJar.Input jarInput = new JavaJar.Input(
        JavaJar.Mode.CreateOrUpdate, 
        targetJar, 
        null, 
        Collections.singletonMap(targetDir, files), 
        classFileOrigin);
    requireBuild(JavaJar.factory, jarInput);
    
    return OutputPersisted.of(targetJar);
  }
}
