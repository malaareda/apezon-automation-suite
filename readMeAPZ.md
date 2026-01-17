1: to review the history of this project on gemini visit this link

>> https://gemini.google.com/share/continue/ba10e0497950
> 
> Note: Please update the above link each time you finish the chat with gemini to keep the link updated

### **Phase 3: Host on GitHub (The "Server")**

We will use a GitHub repository to host these files for free.

1.  **Create a Repo:** Create a new public repository on GitHub (e.g., my-intellij-plugins).

2.  **Upload the ZIP:**

    *   Go to the **Releases** section on the right sidebar.

    *   Draft a new release (e.g., tag v1.0.0).

    *   Upload the ApezonAutomationSuite-1.0.0.zip you built in Phase 1.

    *   Publish the release.

3.  **Get the Download Link:**

    *   Right-click the .zip file you just uploaded in the release and copy the link address.

    *   It should look like: https://github.com/YourName/repo/releases/download/v1.0.0/ApezonAutomationSuite.zip.

4.  **Update the XML:**

    *   Open your updatePlugins.xml.

    *   Paste that link into the url="..." attribute.

    *   Ensure the version="..." matches the version in your build.gradle.kts.

5.  **Upload the XML:**

    *   Upload updatePlugins.xml to the **root** of your GitHub repository code (not the release, but the main file list).

6.  **Get the Raw XML Link:**

    *   Click on updatePlugins.xml in GitHub.

    *   Click the **Raw** button.

    *   Copy the URL. It looks like: https://raw.githubusercontent.com/YourName/repo/main/updatePlugins.xml.


**This Raw URL is your "Server URL".**

### **Phase 4: Install on Any IDE**

Now you can go to any computer or IDE to install it.

1.  Open IntelliJ settings (Ctrl+Alt+S).

2.  Go to **Plugins**.

3.  Click the **Gear Icon** (⚙️) at the top right > **Manage Plugin Repositories...**

4.  Click **+** and paste your **Raw XML Link** from Phase 3.

5.  Click OK.

6.  Go back to the **Marketplace** tab in Plugins.

7.  Search for "Apezon". Your plugin will appear there as if it were on the official store.


### **How to Update Later**

1.  Increase version in build.gradle.kts (e.g., 1.0.1).

2.  Run buildPlugin.

3.  Upload new ZIP to GitHub Releases.

4.  Update updatePlugins.xml with the new Version and new ZIP URL.

5.  Push the XML change.

6.  Your IDE will automatically suggest the update!


++++++++++++++++++++++++++++++

### **Configure IntelliJ (One-Time Setup)**

Now, tell jetbrains IDE where to look for updates.

1.  Open IntelliJ IDEA.
2.  Go to **Settings** (Ctrl+Alt+S) > **Plugins**.
3.  Click the **Gear Icon** (⚙️) at the top right > **Manage Plugin Repositories...**
4.  Click the **\+ (Plus)** button.
5.  Add this URL: [https://raw.githubusercontent.com/malaareda/apezon-automation-suite/refs/heads/main/updatePlugins.xml](https://raw.githubusercontent.com/malaareda/apezon-automation-suite/refs/heads/main/updatePlugins.xml)
6.  Click **OK**.

### **Step 4: Verify Installation**

1.  Go back to the **Marketplace** tab in the Plugins window.
2.  Search for **"Apezon"**.
3.  You should see your plugin listed. Click **Install**.

+++++++++++++++++++++++++

### **How to Release Version 0.0.2 (Future Workflow)**

When you make changes and want to update the plugin:

1.  **Build:** Run gradle buildPlugin.
2.  **Copy:** Copy the new .zip to your local releases folder.
3.  **Update XML:**

    *   Change version="0.0.1" to version="0.0.2".
    *   Change the url to point to .../apezon-automation-suite-0.0.2.zip.

4.  **Push:** Commit and push both the new .zip and the updated updatePlugins.xml to GitHub.
5.  **Auto-Update:** IntelliJ will automatically detect the new version (usually on restart or when checking for updates) and prompt you to update.