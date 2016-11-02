package hudson.plugins.virtualbox;

import hudson.Plugin;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * TODO see https://jax-ws.dev.java.net/issues/show_bug.cgi?id=554
 *
 * @author Evgeny Mandrikov
 */
public class VirtualBoxPlugin extends Plugin {

  private static final Logger LOG = Logger.getLogger(VirtualBoxPlugin.class.getName());

  @Override
  public void start() throws Exception {
    LOG.log(Level.INFO, "Starting {0}", getClass().getSimpleName());
    super.start();
  }

  @Override
  public void stop() throws Exception {
    LOG.log(Level.INFO, "Stopping {0}", getClass().getSimpleName());
    // close VirtualBox WEB sessions
    super.stop();
  }

  /**
   * @return all registered {@link VirtualBoxCloud}
   */
  public static List<VirtualBoxCloud> getHosts() {
    List<VirtualBoxCloud> result = new ArrayList<VirtualBoxCloud>();
    Jenkins jenkins = Jenkins.getInstance();
    for (Cloud cloud : jenkins.clouds) {
      if (cloud instanceof VirtualBoxCloud) {
        result.add((VirtualBoxCloud) cloud);
      }
    }
    return result;
  }

  /**
   * @param hostName host name
   * @return {@link VirtualBoxCloud} by specified name, null if not found
   */
  public static VirtualBoxCloud getHost(String hostName) {
    if (hostName == null) {
      return null;
    }
    for (VirtualBoxCloud host : getHosts()) {
      if (hostName.equals(host.getDisplayName())) {
        return host;
      }
    }
    return null;
  }

  /**
   * @param hostName host name
   * @return all registered {@link VirtualBoxMachine} from specified host, empty list if unknown host
   */
  public static List<VirtualBoxMachine> getDefinedVirtualMachines(String hostName) {
    VirtualBoxCloud host = getHost(hostName);
    if (host == null) {
      return Collections.emptyList();
    }
    return host.refreshVirtualMachinesList();
  }

  /**
   * @param hostName           host name
   * @param virtualMachineName virtual machine name
   * @return {@link VirtualBoxMachine} from specified host with specified name, null if not found
   */
  public static VirtualBoxMachine getVirtualBoxMachine(String hostName, String virtualMachineName) {
    if (virtualMachineName == null) {
      return null;
    }
    VirtualBoxCloud host = VirtualBoxPlugin.getHost(hostName);
    if (host == null) {
      return null;
    }
    return host.getVirtualMachine(virtualMachineName);
  }

  /**
   * For UI.
   */
  public void doComputerNameValues(StaplerRequest req, StaplerResponse resp, @QueryParameter("hostName") String hostName)
      throws IOException, ServletException {
    ListBoxModel m = new ListBoxModel();
    List<VirtualBoxMachine> virtualMachines = getDefinedVirtualMachines(hostName);
    if (virtualMachines != null && virtualMachines.size() > 0) {
      for (VirtualBoxMachine vm : virtualMachines) {
        m.add(new ListBoxModel.Option(vm.getName(), vm.getName()));
      }
      m.get(0).selected = true;
    }
    m.writeTo(req, resp);
  }

  public void doSnapshotNameValues(StaplerRequest req, StaplerResponse rsp, @QueryParameter("vm") String vm, @QueryParameter("hostName") String hostName) throws IOException, ServletException {
    ListBoxModel m = new ListBoxModel();
    m.add(new ListBoxModel.Option("", ""));
    VirtualBoxCloud host = getHost(hostName);
    if (host != null) {
      String[] ss  = host.getSnapshots(vm);
      for (String sshot : ss) {
        m.add(new ListBoxModel.Option(sshot, sshot));
      }
    }

    m.writeTo(req, rsp);
  }

  /**
   * Used for discovering {@link VirtualBoxSlave} with specified MAC Address.
   * HTTP 404 Error will be returned, if slave can't be found.
   * <p>
   * For example: if slave named "virtual" has MAC Adress 080027E852CC, then
   * http://localhost:8080/hudson/plugin/virtualbox/getSlaveAgent?macAddress=080027E852CC
   * redirects to
   * http://localhost:8080/hudson/computer/virtual/slave-agent.jnlp
   * </p>
   *
   * @param req        request
   * @param resp       response
   * @param macAddress MAC Address
   * @throws IOException if something wrong
   */
  public void doGetSlaveAgent(StaplerRequest req, StaplerResponse resp, @QueryParameter("macAddress") String macAddress)
      throws IOException {
    LOG.log(Level.INFO, "Searching VirtualBox machine with MacAddress {0}", macAddress);
    Jenkins jenkins = Jenkins.getInstance();
    for (Node node : jenkins.getNodes()) {
      if (node instanceof VirtualBoxSlave) {
        VirtualBoxSlave slave = (VirtualBoxSlave) node;
        VirtualBoxMachine vbox = getVirtualBoxMachine(slave.getHostName(), slave.getVirtualMachineName());

        String vboxMacAddress = VirtualBoxUtils.getMacAddress(vbox);
        LOG.log(Level.INFO, "MacAddress for {0} is {1}", new Object[]{slave.getNodeName(), vboxMacAddress});

        if (macAddress.equalsIgnoreCase(vboxMacAddress)) {
          String url = jenkins.getRootUrl() + "/computer/" + slave.getNodeName() + "/slave-agent.jnlp";
          LOG.log(Level.INFO, "Found {0} for Mac Address {1}, sending redirect to {2}", new Object[]{slave, macAddress, url});
          resp.sendRedirect(url);
          return;
        }
      }
    }
    resp.sendError(404);
  }

  /**
   * For UI.
   * Checks the number of seconds to wait for a virtual machine to be ready.
   */
  public FormValidation doCheckStartupWaitingPeriodSeconds (@QueryParameter String secsValue) throws IOException, ServletException {
    try {
      int v = Integer.parseInt(secsValue);
      if (v < 0) {
        return FormValidation.error("Negative value..");
      } else if (v == 0) {
        return FormValidation.warning("You declared this virtual machine to be ready right away. It probably needs a couple of seconds before it is ready to process jobs!");
      } else {
        return FormValidation.ok();
      }
    } catch (NumberFormatException e) {
      return FormValidation.error("Not a number..");
    }
  }
}
