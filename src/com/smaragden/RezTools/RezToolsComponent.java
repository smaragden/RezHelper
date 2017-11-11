package com.smaragden.RezTools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class RezToolsComponent implements ProjectComponent {
    private Project currentProject;
    private boolean configured = false;
    public RezToolsComponent(Project project) {
        currentProject = project;
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "RezToolsComponent";
    }

    @NonNls
    @NotNull
    private String createRezScript() {
        /**
         * Create the python rez_info.py file on disk so that we can run it.
         * Return: Path to the written file
         */
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

    private void removeSdks(String name){
        /**
         * Remove all registered SDKs that matches the parameter name.
         */
        ApplicationManager.getApplication().runWriteAction(() -> {
            for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
                System.out.println(String.format("%s, %s", sdk.getName(), name));
                if (name.equals(sdk.getName())){
                    ProjectJdkTable.getInstance().removeJdk(sdk);
                }

            }
        });
    }

    @Nullable
    private Sdk createSdk(String name, SdkType sdkType, VirtualFile path, Set<VirtualFile> paths) {
        /**
         * Create a new SDK with a rez package based name, and add all dependencyu libraries.
         */
        String sdk_name = String.format("REZ(%s) %s", name, sdkType.suggestSdkName(name, path.getPath()));

        // Clear out old sdks for this project
        removeSdks(sdk_name);
        ProjectJdkImpl sdk = new ProjectJdkImpl(sdk_name, sdkType);

        //
        AtomicReference<PythonSdkAdditionalData> additionalData = new AtomicReference<>(new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath())));

        try {
            additionalData.get().setAddedPathsFromVirtualFiles(paths);
        }catch(Exception e){
            System.err.println(e.getMessage());
        }
        sdk.setSdkAdditionalData(additionalData.get());
        sdk.setHomePath(path.getPath());
        ApplicationManager.getApplication().runWriteAction(() -> {
            ProjectJdkTable.getInstance().addJdk(sdk);
        });
        return sdk;
    }

    private void replaceSdk(AtomicReference<RezInfo.PackageInfo> info){
        /**
         * Create a new Sdk based on the info from RezInfo.
         */
        final Sdk sdk = createSdk(info.get().getName(), PythonSdkType.getInstance(), info.get().getInterpreter(), info.get().getDependencyPaths());
        if (sdk != null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                ProjectRootManager.getInstance(currentProject).setProjectSdk(sdk);
                SdkConfigurationUtil.setDirectoryProjectSdk(currentProject, sdk);
            });
        }
    }

    @Override
    public void projectOpened() {
        String rezScript = createRezScript();
        AtomicReference<RezInfo.PackageInfo> info = new AtomicReference<>(RezInfo.getRezInfo(rezScript, currentProject.getBasePath()));
        if(!info.get().valid) {
            configured = false;
            return;
        }
        configured = true;
        info.get().dump();
        replaceSdk(info);
    }
}
