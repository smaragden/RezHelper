package com.smaragden.RezTools;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemIndependent;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


class RezInfo {
    class Dependency {
        public String name;
        public VirtualFile path;
    }

    static class PackageInfo {
        public boolean valid = false;
        private String name;
        private VirtualFile interpreter;
        private String[] variants = new String[]{};
        private Map<String, String> dependencies = new HashMap<String, String>();

        String getName(){
            return this.name;
        }

        void setName(String name) {
            this.name = name;
        }

        VirtualFile getInterpreter(){
            return this.interpreter;
        }

        void setInterpreter(String path) {
            this.interpreter = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(path));
        }

        void setVariants(String[] variants) {
            this.variants = variants;
        }
        void addDependency(String name, String path) {
            this.dependencies.put(name, path);
        }

        public Set<VirtualFile> getDependencyPaths(){
            // Convert dependencies to a set of VirtualFiles to path

            Set<VirtualFile> paths =  new HashSet<VirtualFile>();
            for (Map.Entry<String, String> e : this.dependencies.entrySet())
                paths.add(LocalFileSystem.getInstance().findFileByPath(e.getValue()));
            return paths;
        }

        void dump(){
            System.out.println(String.format("Name: %s", this.name));
            System.out.println(String.format("Interpreter: %s", this.interpreter.getPath()));
            System.out.println("Variants:");
            for (String variant : this.variants) {
                System.out.println(String.format("\t%s", variant));
            }
            System.out.println("Dependencies:");
            for (Map.Entry<String, String> e : this.dependencies.entrySet()){
                System.out.println(String.format("\t%s, %s", e.getKey(), e.getValue()));
            }
        }
    }

    static JSONObject getRezInfoJSON(String rezScript, String projectRoot) throws IOException, JSONException {
        System.out.println(projectRoot);
        Runtime rt = Runtime.getRuntime();
        String[] commands = {"rez-python", rezScript, projectRoot};
        Process proc = rt.exec(commands);

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        StringBuilder out = new StringBuilder();
        String s;
        while ((s = stdInput.readLine()) != null) {
            out.append(s);
            System.out.println(s);
        }

        StringBuilder err = new StringBuilder();
        while ((s = stdError.readLine()) != null) {
            err.append(s);
            System.err.println(s);
        }
        try {
            proc.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(out.toString());
        if (proc.exitValue() != 0) {
            JSONObject obj = new JSONObject();
            obj.put("error", err);
            return obj;
        }

        return new JSONObject(out.toString());
    }

    @NotNull
    static PackageInfo getRezInfo(String rezScript, @SystemIndependent String basePath){
        final RezInfo.PackageInfo result = new RezInfo.PackageInfo();
        try{
            JSONObject data = getRezInfoJSON(rezScript, basePath);
            result.setName((String) data.get("name"));
            result.setInterpreter((String) data.get("interpreter"));

            JSONArray variants_data = data.getJSONArray("variants");
            String[] variants = new String[variants_data.length()];
            for (int i = 0; i < variants_data.length(); i++) variants[i] = variants_data.getString(i);
            result.setVariants(variants);

            JSONObject dependencies = data.getJSONObject("dependencies");
            Iterator<?> dep_keys = dependencies.keys();
            while( dep_keys.hasNext() ) {
                String name = (String) dep_keys.next();
                String path = (String) dependencies.get(name);
                result.addDependency(name, path);
            }
            result.valid = true;
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
