[![Maven Central](https://img.shields.io/maven-central/v/com.nordstrom.tools/remote-session.svg)](https://mvnrepository.com/artifact/com.nordstrom.tools/remote-session)

# REMOTE SESSION UTILITIES

**Remote Session** is a small collection of utility classes for interacting with remote systems via Secure Shell (SSH) protocol. Built around the [JSch](http://www.jcraft.com/jsch/) library from _JCraft_, available functions include:

* Secure file transfer
* Remote interactive shell
* Remote command execution

**Remote Session** wraps the capabilities of **JSch** in a simplified API that handles many of the details related to setting up and managing remote sessions. Originated by [Mykhaylo Adamovych](http://stackoverflow.com/users/448078/mykhaylo-adamovych), the implementation was copied verbatim from a post on [Stack Overflow](http://stackoverflow.com/questions/2405885/run-a-command-over-ssh-with-jsch). JavaDoc has been added for completeness and comprehensibility.

## Continuing Development

The last release of the **JSch** library from the original [SourceForge project](https://sourceforge.net/projects/jsch/) was published 2018-NOV-26. Continuing development of this project has been taken up by **_Matthias Wiedemann_** [here](https://github.com/mwiede/jsch), with new releases published at these artifact coordinates:

| Maven |
|:---|
| <pre>&lt;dependency&gt;<br/>&nbsp;&nbsp;&lt;groupId&gt;com.github.mwiede&lt;/groupId&gt;<br/>&nbsp;&nbsp;&lt;artifactId&gt;jsch&lt;/artifactId&gt;<br/>&nbsp;&nbsp;&lt;version&gt;0.2.24&lt;/version&gt;<br/>&lt;/dependency&gt;</pre> |

| Gradle |
|:---|
| <pre>dependencies {<br/>&nbsp;&nbsp;compile 'com.github.mwiede:jsch:0.2.24'<br/>}</pre> |

This new incarnation of the **JSch** project is a drop-in replacement for artifacts published from the original project. However, support for older or deprecated algorithms is disabled by default. Information on compatibility and configuration can be found on the project's [README](https://github.com/mwiede/jsch/blob/master/Readme.md#fork-of-jsch-0155) page. 

## Secure File Transfer

**Remote Session** enables clients to upload and download files via secure file transfer protocol (SFTP).

```java
    SshUtils.sftp("file:/C:/home/file.txt", "ssh://user:pass@host/home");
    SshUtils.sftp("ssh://user:pass@host/home/file.txt", "file:/C:/home");
```

**NOTE**: The transferred file retains its original name. If specified, the name component of `toUrl` will be ignored.  
**NOTE**: As indicated by the examples, source and target URIs must refer to opposing locations: `file` for local file system and `ssh` for remote file system.

* For upload: **fromUri** = `file`; **toUri** = `ssh`
* For download: **fromUri** = `ssh`; **toUri** = `file`

## Remote Interactive Shell

**Remote Session** supports interacting with remote systems via a secure `shell` channel, in which input and output are streamed between local and remote systems. Each of the three methods provided for secure shell interaction exhibit different operational characteristics.

###### Unbounded Stream I/O
```java
import com.nordstrom.remote.SshUtils;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;

...

    public void example() {
        InputStream is = System.in;
        OutputStream os = System.out;
        SshUtils.shell("ssh://user:pass@host", is, os);
        PrintStream ps = new PrintStream(is, true);
        ps.println("ls -la");
        ps.println("exit");
        System.out.println(IOUtils.toString(os, Charset.defaultCharset()));
    }
```

With unbounded stream I/O, the channel remains open until the input stream is closed or an `exit` command is submitted.

###### Submit Specified Command
```java
    String remoteOutput = SshUtils.shell("ssh://user:pass@host/work/dir/path", "ls");
```

From the client perspective, this is effectively equivalent to `exec(String, String)`. The primary difference is the channel used for communication (`shell` instead of `exec`).

###### Submit Command with Streamed Output
```java
import com.nordstrom.remote.SshUtils;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;

...

    public void example() {
        OutputStream os = System.out;
        SshUtils.shell("ssh://user:pass@host", "ls -la", os);
        System.out.println(IOUtils.toString(os, Charset.defaultCharset()));
    }
```

This is essentially a hybrid of the previous two secure-shell methods, well-suited for long-running commands that you'd like to leave running while you handle other tasks.

## Remote Command Execution

**Remote Session** enables you to submit commands to remote systems atomically. Instead of submitting commands through an input stream, you specify each command as a property of the channel and execute it.

###### Execute Specified Command
```java
    System.out.println(SshUtils.exec("ssh://user:pass@host/work/dir/path", "ls -t | head -n1"));
```

###### Execute Command 
```java
import java.net.URI;
import com.nordstrom.remote.SshUtils.SessionHolder;
import com.nordstrom.remote.SshUtils.ChannelType;
import com.jcraft.jsch.ChannelExec;

...

    public void example() {
        String connectUri = "ssh://user:pass@host/work/dir/path";
        String command = "ls -t | head -n1";
        try (SessionHolder<ChannelExec> session = new SessionHolder<>(ChannelType.EXEC, URI.create(connectUri))) {
            String workDir = session.getWorkDir();
            if (workDir != null) command = "cd " + workDir + " && " + command;
            System.out.println(session, command);
        }
    }
```

## JSch Reference Implementation (BatchUtils)

**BatchUtils** is a reference implementation of a **JSch** client. It enables you to execute the specified command, optionally executing an initial command to switch to an alternate user first.

```java
    String userName = "user";
    String password = "password";
    String hostName = "host";
    String sudoCmd = "sudo su - admin";
    String batchDir = "/work/dir/path";
    String batchCmd = "./script.ksh parm1 parm2";
    String output = BatchUtils.executeBatch(userName, password, hostName, sudoCmd, batchDir, batchCmd);
    System.out.println(output);
```

The implementation of `BatchUtils` demonstrates how to use a couple of important **Remote Session** classes:
* `SessionHolder` - This is a wrapper class for objects that extend the `Channel` class. The wrapper implements the `Closeable` interface, and `BatchUtils` uses a "try-with-resources" block to ensure that the channel is always closed regardless of the outcome of command execution. `SessionHolder` includes these methods (among others):
  * `getChannel` - Get the channel to the remote session created for this `SessionHolder`.
  * `getChannelStream` - Get a new channel stream object for this session.
  * `disconnect` - Disconnect channel and session.
  * `assertExitStatus` - Verifies that the remote task completed normally.
* `ChannelStreams` - This class encapsulates input/output operation for the channel attached to this session. It includes these methods:
  * `waitForInput` - Wait for input to be available.
  * `writeln` - Write the specified string to the remote host, followed by a carriage return.
  * `waitForPrompt` - Wait for the specified prompt to be received from the remote host.
  * `readChannel` - Read the input from the channel.
  
## **Remote Session** Settings

**Remote Session** provides a number of important settings that can be configured through system properties or a corresponding `properties` file (_remote.properties_). The library provides default values for all settings in the **RemoteConfig** class.

| Setting | Property Name | Default |
| --- | --- |:---:|
| **`SSH_KEY_NAME`** | `remote.ssh.key.name` | `id_rsa` |
| **`SSH_KEY_PASS`** | `remote.ssh.key.pass` | _(none)_ |
| **`IGNORE_KNOWN_HOSTS`** | `remote.ignore.known.hosts` | `false` |
| **`SESSION_CONNECT_TIMEOUT`** | `remote.session.connect.timeout` | `5000` |
| **`SSH_PORT_NUMBER`** | `remote.ssh.port.number` | `22` |
| **`TERMINAL_HEIGHT`** | `remote.terminal.height` | `24` |
| **`TERMINAL_WIDTH`** | `remote.terminal.width` | `132` |
| **`TERMINAL_H_RESOLUTION`** | `remote.terminal.h.resolution` | `924` |
| **`TERMINAL_V_RESOLUTION`** | `remote.terminal.v.resolution` | `216` |
| **`COMPLETION_CHECK_INTERVAL`** | `remote.completion.check.interval` | `100` |
| **`DISCONNECT_CHECK_ATTEMPTS`** | `remote.disconnect.check.attempts` | `600` |
| **`DISCONNECT_CHECK_INTERVAL`** | `remote.disconnect.check.interval` | `100` |
| **`CHANNEL_CHECK_INTERVAL`** | `remote.channel.check.interval` | `100` |
| **`CHANNEL_BUFFER_SIZE`** | `remote.channel.buffer.size` | `102400` |

### Setting Details

The **`SSH_KEY_NAME`** setting specifies the path to an SSH key file for authentication to the remote host. If the key file is specified by full path, this is used as-is. Otherwise, the key file must be located in the .ssh folder of the active user's HOME directory. If the key file is encrypted, you must provide the decryption passphrase in the **`SSH_KEY_PASS`** setting. This also implies the presence of a corresponding `pub` file in the same folder as the key file.

If a _known_hosts_ file is stored in the same folder as the SSH key file(s), this _known_hosts_ file will be supplied to **JSch** as your personal Certificate Authority. The **`IGNORE_KNOWN_HOSTS`** setting specifies that this _known_hosts_ file should be ignored.

> **NOTE**: If credentials are specified in the remote host URL, the `SSH_KEY_NAME` and `SSH_KEY_PASS` settings are ignored. Also, no attempt is made to locate a _known_hosts_ file for **JSch**.

The **`SESSION_CONNECT_TIMEOUT`** setting is the interval in milliseconds that **JSch** will wait for socket connection operations to complete. If this value is set to **`0`**, no timeout will be established.

The **`COMPLETION_CHECK_INTERVAL`** setting is the interval in milliseconds between END-OF-FILE checks for remote command execution.

The **`DISCONNECT_CHECK_INTERVAL`** setting in the interval in milliseconds between checks for the closure of the remote session channel.

The **`CHANNEL_CHECK_INTERVAL`** setting is the interval in millisecinds between checks for available input from the remote session channel.
