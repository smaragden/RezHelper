package com.smaragden.RezTools;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.apache.commons.lang.ArrayUtils;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class RezModule implements ModuleComponent {
    private Project currentProject;
    private Module currentModule;
    public RezModule(Module module) {
        currentModule = module;
        System.out.println(String.format("Module: %s", currentModule.getName()));
    }

    @Override
    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    @Override
    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "RezModule";
    }

    @Override
    public void moduleAdded() {
        // Invoked when the module corresponding to this component instance has been completely
        // loaded and added to the project.
    }

    private JSONObject getRezInfo(String projectRoot) throws IOException, JSONException {
        String rezScript = createRezScript();

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
        }

        StringBuilder err = new StringBuilder();
        while ((s = stdError.readLine()) != null) {
            err.append(s);
        }
        try {
            proc.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (proc.exitValue() != 0) {
            JSONObject obj = new JSONObject();
            obj.put("error", err);
            return obj;
        }
        return new JSONObject(out.toString());
    }

    @NonNls
    @NotNull
    private String createRezScript() {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("scripts/rez_info.py");
        String rezScript = "/tmp/rez_info.py";
        String rezScriptContent;
        try {
            rezScriptContent = StreamUtil.readText(resource, "UTF-8");
            Files.write(Paths.get(rezScript), rezScriptContent.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create REZ script.", e);
        }
        return rezScript;
    }

    @Nullable
    private Sdk createSdk(String name, SdkType sdkType, VirtualFile path) {
        String sdk_name = String.format("%s %s", name, sdkType.suggestSdkName(name, path.getPath()));
        ProjectJdkImpl sdk = new ProjectJdkImpl(sdk_name, sdkType);

        //
        PythonSdkAdditionalData additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
        //PythonSdkAdditionalData additionalData = (PythonSdkAdditionalData)sdk.getSdkAdditionalData();
        //additionalData.associateWithProject(currentProject);



        Set<VirtualFile> paths =  new HashSet<VirtualFile>();

        VirtualFile vp = LocalFileSystem.getInstance().findFileByPath("/home/fredrik.brannbacka/packages/MarkupSafe/1.0/platform-linux/arch-x86_64/os-CentOS-7.3.1611/python-2.7/python");
        System.out.println(vp.getPath());
        paths.add(vp);
        try {
            assert additionalData != null;
            additionalData.setAddedPathsFromVirtualFiles(paths);
        }catch(Exception e){
            System.err.println(e.getMessage());
        }
        /*
        */
        sdk.setSdkAdditionalData(additionalData);
        sdk.setHomePath(path.getPath());
        //final Sdk sdk = ProjectJdkTable.getInstance().createSdk(sdk_name, sdkType);
        //SdkModificator sdkModificator = sdk.getSdkModificator();
        //sdkModificator.setHomePath(path.getPath());


        //
        //sdkModificator.commitChanges();
        ApplicationManager.getApplication().runWriteAction(() -> {
            ProjectJdkTable.getInstance().addJdk(sdk);
        });
        return sdk;

    }

    @Override
    public void projectOpened() {
        currentProject = currentModule.getProject();
        Logger.getInstance(getClass().getName()).info("REZ: Project Opened");
        Logger.getInstance(getClass().getName()).info(String.format("Iterpreter Before: %s", ProjectRootManager.getInstance(currentProject).getProjectSdkName()));
        JSONObject rez_info = null;
        try {
            rez_info = getRezInfo(currentProject.getBasePath());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        try {
            assert rez_info != null;
            System.out.println(rez_info.toString(2));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (rez_info.has("error")) {
            try {
                System.err.println(rez_info.getString("error"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return;
        }

        ;
        try {
            String name = String.valueOf(rez_info.get("name"));
            String path = String.valueOf(rez_info.get("interpreter"));
            JSONArray dependencies = rez_info.getJSONArray("dependencies");
            String[] paths = new String[dependencies.length()];
            for (int i = 0; i < dependencies.length(); i++) {
                JSONObject obj = dependencies.getJSONObject(i);
                Iterator keys = obj.keys();
                while(keys.hasNext()) {
                    Object element = keys.next();
                    paths[i] = obj.getString(element.toString());
                }
            }
            System.out.println(String.join(", ", paths));

                VirtualFile sdkHome =
                    WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(path));
            if (sdkHome != null) {
                final Sdk sdk = createSdk(name, PythonSdkType.getInstance(), sdkHome);
                if (sdk != null) {
                    Logger.getInstance(getClass().getName()).info(String.format("Iterpreter: %s", sdk.getName()));
                    Logger.getInstance(getClass().getName()).info(String.format("Iterpreter Before: %s", ProjectRootManager.getInstance(currentProject).getProjectSdkName()));
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        public void run() {
                            ProjectRootManager.getInstance(currentProject).setProjectSdk(sdk);
                            SdkConfigurationUtil.setDirectoryProjectSdk(currentProject, sdk);
                            Logger.getInstance(getClass().getName()).info(String.format("Iterpreter After: %s", ProjectRootManager.getInstance(currentProject).getProjectSdkName()));
                        }
                    });
                }
            }

        } catch (JSONException e) {
            System.err.println(String.format("Something went wrong: %s", e.getMessage()));
            e.printStackTrace();
        }


        //SdkConfigurationUtil.createSdk();
        //VirtualFile iterpreterPath = LocalFileSystem.getInstance().findFileByIoFile(new File("/home/fredrik.brannbacka/packages/python/2.7.13/platform-linux/bin/python2.7"));
        //Sdk sdk = SdkConfigurationUtil.setupSdk(NO_SDK, iterpreterPath, PythonSdkType.getInstance(), true, null, null);

        /*
        Sdk sdk = SdkConfigurationUtil.createAndAddSDK("/home/fredrik.brannbacka/packages/python/2.7.13/platform-linux/bin/python2.7", PythonSdkType.getInstance());
            Runnable r = ()-> {
            ProjectRootManager.getInstance(currentProject).setProjectSdk(sdk);
            ProjectRootManager.getInstance(currentProject).setProjectSdkName("bajskorvsiterpretern");
        };

        WriteCommandAction.runWriteCommandAction(currentProject, r);
        */
        //Logger.getInstance(getClass().getName()).info(projectSdk.);
    }

    @Override
    public void projectClosed() {
        Logger.getInstance(getClass().getName()).info("REZ: Project Closed");
        Logger.getInstance(getClass().getName()).info(String.format("Iterpreter on close: %s", ProjectRootManager.getInstance(currentProject).getProjectSdkName()));
    }
}
