package com.nordstrom.remote;

import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Optional;

import org.apache.sshd.server.SshServer;
import org.testng.ITestResult;
import org.testng.Reporter;

import com.nordstrom.automation.testng.ExecutionFlowController;
import com.nordstrom.automation.testng.LinkedListeners;
import com.nordstrom.automation.testng.TrackedObject;

@LinkedListeners({ExecutionFlowController.class})
public abstract class TestNgBase {

    /**
     * This enumeration is responsible for storing and retrieving values in the attributes collection of the current
     * test result, as reported by {@link Reporter#getCurrentTestResult()}. 
     */
    private enum TestAttribute {
        SSH_SERVER("SshServer"),
        SERVER_PORT("ServerPort"),
        REMOTE_ROOT("RemoteRoot"),
        PUBLIC_KEY("PublicKey"),
        PRIVATE_KEY_PATH("PrivateKeyPath");
        
        private String key;
        
        /**
         * Constructor for TestAttribute enumeration
         * 
         * @param key key for this constant
         */
        TestAttribute(final String key) {
            this.key = key;
        }
        
        /**
         * Store the specified object in the attributes collection.
         * 
         * @param obj object to be stored; 'null' to discard value
         */
//        private void set(final Object obj) {
//            ITestResult result = Reporter.getCurrentTestResult();
//            if (obj != null) {
//                result.setAttribute(key, obj);
//            } else {
//                result.removeAttribute(key);
//            }
//        }
        
        /**
         * Store the specified object in the attributes collection, tracking reference propagation.
         * 
         * @param obj object to be stored; 'null' to discard value and release tracked references
         */
        private void track(final Object obj) {
            ITestResult result = Reporter.getCurrentTestResult();
            if (obj != null) {
                new TrackedObject<>(result, key, obj);
            } else {
                Object val = result.getAttribute(key);
                if (val instanceof TrackedObject) {
                    ((TrackedObject<?>) val).release();
                } else {
                    result.removeAttribute(key);
                }
            }
        }
        
        /**
         * If present, get the object from the attributes collection.
         * 
         * @return (optional) stored object
         */
        private Optional<?> nab() {
            Object obj;
            ITestResult result = Reporter.getCurrentTestResult();
            Object val = result.getAttribute(key);
            if (val instanceof TrackedObject) {
                obj = ((TrackedObject<?>) val).getValue();
            } else {
                obj = val;
            }
            return optionalOf(obj);
        }
    }
    
    public SshServer getSshServer() {
        return TestAttribute.SSH_SERVER.nab()
                .map(SshServer.class::cast)
                .orElseThrow(() -> new RuntimeException("Test attribute 'SSH_SERVER' not initialized"));
    }
    
    public void setSshServer(final SshServer sshServer) {
        TestAttribute.SSH_SERVER.track(sshServer);
    }
    
    public Integer getServerPort() {
        return TestAttribute.SERVER_PORT.nab()
                .map(Integer.class::cast)
                .orElseThrow(() -> new RuntimeException("Test attribute 'SERVER_PORT' not initialized"));
    }
    
    public void setServerPort(final Integer serverPort) {
        TestAttribute.SERVER_PORT.track(serverPort);
    }
    
    public Path getRemoteRoot() {
        return TestAttribute.REMOTE_ROOT.nab()
                .map(Path.class::cast)
                .orElseThrow(() -> new RuntimeException("Test attribute 'REMOTE_ROOT' not initialized"));
    }
    
    public void setRemoteRoot(final Path remoteRoot) {
        TestAttribute.REMOTE_ROOT.track(remoteRoot);
    }
    
    public PublicKey getPublicKey() {
        return TestAttribute.PUBLIC_KEY.nab()
                .map(PublicKey.class::cast)
                .orElseThrow(() -> new RuntimeException("Test attribute 'PUBLIC_KEY' not initialized"));
    }
    
    public void setPublicKey(final PublicKey publicKey) {
        TestAttribute.PUBLIC_KEY.track(publicKey);
    }
    
    public Path getPrivateKeyPath() {
        return TestAttribute.PRIVATE_KEY_PATH.nab()
                .map(Path.class::cast)
                .orElseThrow(() -> new RuntimeException("Test attribute 'PRIVATE_KEY_PATH' not initialized"));
    }
    
    public void setPrivateKeyPath(final Path privateKeyPath) {
        TestAttribute.PRIVATE_KEY_PATH.track(privateKeyPath);
    }
    
    /**
     * Wrap the specified object in an {@link Optional} object.
     * 
     * @param <T> type of object to be wrapped
     * @param obj object to be wrapped (may be 'null')
     * @return (optional) wrapped object; empty if {@code obj} is 'null'
     */
    public static <T> Optional<T> optionalOf(T obj) {
        if (obj != null) {
            return Optional.of(obj);
        } else {
            return Optional.empty();
        }
    }
}
