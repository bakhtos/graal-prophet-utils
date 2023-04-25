package baylor.cloudhubs.prophetutils.nativeimage;

import com.google.gson.Gson;

import baylor.cloudhubs.prophetutils.ProphetUtilsFacade;
import baylor.cloudhubs.prophetutils.systemcontext.Module;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NativeImageRunner {
    private final String classpath;
    private final String entityOutput;
    private final String restcallOutput;
    private final String endpointOutput;

    private final MicroserviceInfo msInfo;
    private final String niCommand;
    

    public NativeImageRunner(MicroserviceInfo info, String graalProphetHome, String outputDir) {
        this.niCommand = graalProphetHome + "/bin/native-image";
        this.msInfo = info;
        String microservicePath = info.getBaseDir();
        this.classpath = microservicePath + "/target/BOOT-INF/classes" + ":" + microservicePath + "/target/BOOT-INF/lib/*";
        this.entityOutput = "./" + outputDir + "/" + info.getMicroserviceName() + ".json";
        this.restcallOutput = "./" + outputDir + "/"  + info.getMicroserviceName() + "_restcalls.csv";
        this.endpointOutput = "./" + outputDir + "/"  + info.getMicroserviceName() + "_endpoints.csv";
    }

    public Module runProphetPlugin() {
        executeNativeImage();
        return parseOutputFile();
    }

    private Module parseOutputFile() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(entityOutput)) {
            return gson.fromJson(reader, Module.class);
        }
        catch(FileNotFoundException fne){
            System.out.println("WARNING: FILE '" + entityOutput + "' NOT FOUND, LIKELY ANALYSIS FAILED");
            
            //increment attempt for running microservice
            ProphetUtilsFacade.MS_TO_ANALYZE.put(this.msInfo.getMicroserviceName(), ProphetUtilsFacade.MS_TO_ANALYZE.get(this.msInfo.getMicroserviceName()) + 1);
            //if microservice failed third attempt, throw error
            if (ProphetUtilsFacade.MS_TO_ANALYZE.get(this.msInfo.getMicroserviceName()) >= ProphetUtilsFacade.RETRY_MAX){
                throw new RuntimeException("ERROR: " + this.msInfo.getMicroserviceName() + " FAILED TO BE ANALYZED");
            }
            return null;
        } 
        catch (IOException e) {
            System.out.println("ERROR: IOException RUNNING ON " + this.msInfo.getMicroserviceName());
            throw new RuntimeException(e);
        }
    }

    private void executeNativeImage() {
        List<String> cmd = prepareCommand();
        
        // System.out.println(String.join(" ", cmd));
        try {
            Process process = new ProcessBuilder()
                    .command(cmd)
                    .inheritIO()
                    .start();
            int res = process.waitFor();
            if (res != 0) {
                System.err.println("Failed to execute command.");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        // String[] cmd = {
        //     niCommand,
        //     "-H:+ProphetPlugin",
        //     "-H:-InlineBeforeAnalysis",
        //     "-H:+BuildOutputSilent",
        //     "-H:ProphetMicroserviceName=" + this.info.getMicroserviceName(),
        //     "-H:ProphetBasePackage=" + this.info.getBasePackage(),
        //     "-H:ProphetEntityOutputFile=" + this.entityOutput,   
        //     "-H:ProphetRestCallOutputFile=" + this.restcallOutput,        
        //     "-H:ProphetEndpointOutputFile=" + this.endpointOutput,        
        //     "-cp",
        //     classpath,
        //     this.info.getMicroserviceName()
        // };
        // String commandStr = niCommand +  " --gc=G1 -H:+ProphetPlugin -H:-InlineBeforeAnalysis -H:+BuildOutputSilent -H:ProphetMicroserviceName=" + this.info.getMicroserviceName() + 
        //     " -H:ProphetBasePackage=" + this.info.getBasePackage() + " -H:ProphetEntityOutputFile=" + this.entityOutput + " -H:ProphetRestCallOutputFile=" + this.restcallOutput + 
        //     " -H:ProphetEndpointOutputFile=" + this.endpointOutput + " -cp " + classpath + " " + this.info.getMicroserviceName();
        // System.out.println("command = " + commandStr);
        // Runtime r = Runtime.getRuntime();
        // try {
        //     Process p = r.exec(commandStr);
        //     int exitVal = p.waitFor();
        //     if (exitVal != 0){
        //         throw new IOException("Process did not complete successfully");
        //     }
        // } catch (IOException | InterruptedException e ) {
        //     e.printStackTrace();
        // }
    }

    @NotNull
    private List<String> prepareCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(niCommand);
        cmd.add("-H:+ProphetPlugin");
        cmd.add("-H:-InlineBeforeAnalysis");
        cmd.add("-H:+BuildOutputSilent");
        cmd.add("-H:ProphetMicroserviceName=" + this.msInfo.getMicroserviceName());
        cmd.add("-H:ProphetBasePackage=" + this.msInfo.getBasePackage());
        cmd.add("-H:ProphetEntityOutputFile=" + this.entityOutput);   
        cmd.add("-H:ProphetRestCallOutputFile=" + this.restcallOutput);        
        cmd.add("-H:ProphetEndpointOutputFile=" + this.endpointOutput);
        // cmd.add("-R:MinHeapSize=4m"); 
        // cmd.add("-R:MaxHeapSize=15m");
        // cmd.add("-R:MaxNewSize=2m");   
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(this.msInfo.getMicroserviceName());
        return cmd;
    }
}
