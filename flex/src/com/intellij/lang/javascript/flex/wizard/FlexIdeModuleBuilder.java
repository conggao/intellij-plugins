package com.intellij.lang.javascript.flex.wizard;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.projectStructure.FlexIdeBCConfigurator;
import com.intellij.lang.javascript.flex.projectStructure.FlexIdeModuleStructureExtension;
import com.intellij.lang.javascript.flex.projectStructure.FlexSdk;
import com.intellij.lang.javascript.flex.projectStructure.model.ModifiableFlexIdeBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.OutputType;
import com.intellij.lang.javascript.flex.projectStructure.model.TargetPlatform;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.Factory;
import com.intellij.lang.javascript.flex.projectStructure.model.impl.FlexProjectConfigurationEditor;
import com.intellij.lang.javascript.flex.run.FlexIdeRunConfiguration;
import com.intellij.lang.javascript.flex.run.FlexIdeRunConfigurationType;
import com.intellij.lang.javascript.flex.run.FlexIdeRunnerParameters;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class FlexIdeModuleBuilder extends ModuleBuilder {

  private TargetPlatform myTargetPlatform = TargetPlatform.Web;
  private boolean isPureActionScript = false;
  private OutputType myOutputType = OutputType.Application;
  private FlexSdk myFlexSdk;
  private String myTargetPlayer;
  private boolean myCreateSampleApp;
  private String mySampleAppName;
  private boolean myCreateHtmlWrapperTemplate;

  public ModuleType getModuleType() {
    return FlexModuleType.getInstance();
  }

  public void setTargetPlatform(final TargetPlatform targetPlatform) {
    myTargetPlatform = targetPlatform;
  }

  public void setPureActionScript(final boolean pureActionScript) {
    isPureActionScript = pureActionScript;
  }

  public void setOutputType(final OutputType outputType) {
    myOutputType = outputType;
  }

  public void setFlexSdk(final FlexSdk flexSdk) {
    myFlexSdk = flexSdk;
  }

  public void setTargetPlayer(final String targetPlayer) {
    myTargetPlayer = targetPlayer;
  }

  public void setCreateSampleApp(final boolean createSampleApp) {
    myCreateSampleApp = createSampleApp;
  }

  public void setSampleAppName(final String sampleAppName) {
    mySampleAppName = sampleAppName;
  }

  public void setCreateHtmlWrapperTemplate(final boolean createHtmlWrapperTemplate) {
    myCreateHtmlWrapperTemplate = createHtmlWrapperTemplate;
  }

  public void setupRootModel(final ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    final ContentEntry contentEntry = doAddContentEntry(modifiableRootModel);
    if (contentEntry == null) return;

    final VirtualFile sourceRoot = createSourceRoot(contentEntry);

    final Module module = modifiableRootModel.getModule();
    final LibraryTableBase.ModifiableModelEx globalLibrariesModifiableModel;

    final FlexProjectConfigurationEditor currentFlexEditor =
      FlexIdeModuleStructureExtension.getInstance().getConfigurator().getConfigEditor();
    final boolean needToCommitFlexEditor = currentFlexEditor == null;

    final FlexProjectConfigurationEditor flexConfigEditor;

    if (currentFlexEditor != null) {
      globalLibrariesModifiableModel = null;
      flexConfigEditor = currentFlexEditor;
    }
    else {
      globalLibrariesModifiableModel =
        (LibraryTableBase.ModifiableModelEx)ApplicationLibraryTable.getApplicationTable().getModifiableModel();
      flexConfigEditor = createFlexConfigEditor(modifiableRootModel, globalLibrariesModifiableModel);
    }

    final ModifiableFlexIdeBuildConfiguration[] configurations = flexConfigEditor.getConfigurations(module);
    assert configurations.length == 1;
    final ModifiableFlexIdeBuildConfiguration bc = configurations[0];

    setupBC(module, bc);

    if (bc.getOutputType() == OutputType.Application) {
      createRunConfiguration(module, bc.getName());
    }

    if (sourceRoot != null && myCreateSampleApp) {
      try {
        final boolean flex4 = myFlexSdk.getFlexVersion().startsWith("4");
        FlexUtils.createSampleApp(module.getProject(), sourceRoot, mySampleAppName, myTargetPlatform, flex4);
      }
      catch (IOException ex) {
        throw new ConfigurationException(ex.getMessage());
      }
    }

    commitIfNeeded(globalLibrariesModifiableModel, flexConfigEditor, needToCommitFlexEditor);
  }

  private static FlexProjectConfigurationEditor createFlexConfigEditor(final ModifiableRootModel modifiableRootModel,
                                                                       final LibraryTableBase.ModifiableModelEx globalLibrariesModifiableModel) {
    final Module module = modifiableRootModel.getModule();

    final FlexProjectConfigurationEditor.ProjectModifiableModelProvider provider =
      new FlexProjectConfigurationEditor.ProjectModifiableModelProvider() {
        public Module[] getModules() {
          return new Module[]{module};
        }

        public ModifiableRootModel getModuleModifiableModel(final Module moduleParam) {
          assert moduleParam == module;
          return modifiableRootModel;
        }

        public void addListener(final FlexIdeBCConfigurator.Listener listener,
                                final Disposable parentDisposable) {
          // modules and BCs are not removed here
        }

        public void commitModifiableModels() throws ConfigurationException {
          // commit will be performed outside of #setupRootModel()
        }

        public LibraryTableBase.ModifiableModelEx getGlobalLibrariesModifiableModel() {
          return globalLibrariesModifiableModel;
        }
      };

    return new FlexProjectConfigurationEditor(modifiableRootModel.getProject(), provider);
  }

  private static void commitIfNeeded(final @Nullable LibraryTableBase.ModifiableModelEx globalLibrariesModifiableModel,
                                     final FlexProjectConfigurationEditor flexConfigEditor,
                                     final boolean needToCommitFlexEditor) throws ConfigurationException {
    if (globalLibrariesModifiableModel != null || needToCommitFlexEditor) {
      final ConfigurationException exception =
        ApplicationManager.getApplication().runWriteAction(new NullableComputable<ConfigurationException>() {
          public ConfigurationException compute() {
            if (globalLibrariesModifiableModel != null) {
              globalLibrariesModifiableModel.commit();
            }

            if (needToCommitFlexEditor) {
              try {
                flexConfigEditor.commit();
              }
              catch (ConfigurationException e) {
                return e;
              }
            }

            return null;
          }
        });

      if (exception != null) {
        throw exception;
      }
    }
  }

  private void setupBC(final Module module, final ModifiableFlexIdeBuildConfiguration bc) {
    bc.setName(module.getName());
    bc.setTargetPlatform(myTargetPlatform);
    bc.setPureAs(isPureActionScript);
    bc.setOutputType(myOutputType);

    final String className = FileUtil.getNameWithoutExtension(mySampleAppName);

    if (myCreateSampleApp) {
      bc.setMainClass(className);
      bc.setOutputFileName(className + (myOutputType == OutputType.Library ? ".swc" : ".swf"));
    }
    else {
      bc.setOutputFileName(module.getName() + (myOutputType == OutputType.Library ? ".swc" : ".swf"));
    }
    bc.setOutputFolder(VfsUtil.urlToPath(CompilerModuleExtension.getInstance(module).getCompilerOutputUrl()));

    bc.getDependencies().setSdkEntry(Factory.createSdkEntry(myFlexSdk.getLibraryId(), myFlexSdk.getHomePath()));
    bc.getDependencies().setTargetPlayer(myTargetPlayer);

    if (myTargetPlatform == TargetPlatform.Mobile && myOutputType == OutputType.Application) {
      bc.getAndroidPackagingOptions().setEnabled(true);
      bc.getAndroidPackagingOptions().setPackageFileName(className + ".apk");
      bc.getIosPackagingOptions().setPackageFileName(className + ".ipa");
    }
  }

  public static void createRunConfiguration(final Module module, final String bcName) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(module.getProject());

    final String suggestedName = bcName.equals(module.getName()) ? bcName : (bcName + " (" + module.getName() + ")");
    final String name = getUniqueName(suggestedName, runManager.getConfigurations(FlexIdeRunConfigurationType.getInstance()));
    final RunnerAndConfigurationSettings settings = runManager.createConfiguration(name, FlexIdeRunConfigurationType.getFactory());
    settings.setTemporary(false);
    runManager.addConfiguration(settings, false);
    runManager.setActiveConfiguration(settings);

    final FlexIdeRunConfiguration runConfiguration = (FlexIdeRunConfiguration)settings.getConfiguration();
    final FlexIdeRunnerParameters params = runConfiguration.getRunnerParameters();
    params.setModuleName(module.getName());
    params.setBCName(bcName);
  }

  private static String getUniqueName(final String suggestedName, final RunConfiguration[] existingConfigurations) {
    final String[] used = new String[existingConfigurations.length];
    for (int i = 0; i < existingConfigurations.length; i++) {
      used[i] = existingConfigurations[i].getName();
    }
    
    if (ArrayUtil.contains(suggestedName, used)) {
      int i = 1;
      String name;
      while (ArrayUtil.contains((name = suggestedName + " (" + i + ")"), used)) {
        i++;
      }
      return name;
    }
    
    return suggestedName;
  }

  @Nullable
  private VirtualFile createSourceRoot(final ContentEntry contentEntry) {
    final VirtualFile contentRoot = contentEntry.getFile();
    if (contentRoot == null) return null;

    VirtualFile sourceRoot = VfsUtil.findRelativeFile(contentRoot, "src");

    if (sourceRoot == null) {
      sourceRoot = ApplicationManager.getApplication().runWriteAction(new NullableComputable<VirtualFile>() {
        public VirtualFile compute() {
          try {
            return contentRoot.createChildDirectory(this, "src");
          }
          catch (IOException e) {
            return null;
          }
        }
      });
    }

    if (sourceRoot != null) {
      contentEntry.addSourceFolder(sourceRoot, false);
      return sourceRoot;
    }

    return null;
  }
}
