package demo.cd.be.model;

import java.util.*;
import java.io.*;
import java.text.*;
import java.net.*;

import org.springframework.stereotype.*;

import lombok.extern.slf4j.Slf4j;

@Component
public class DeployInfo {
  private static SimpleDateFormat BASIC_DATE_FMT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");

  public Map<String, Object> getData() throws Exception {
    Map<String, String> env = System.getenv();
    
    Map<String,Object> res = new HashMap<>();
    
    Map<String,Object> sys = new HashMap<>();
    
    sys.put("Hostname", env.get("HOSTNAME"));
    sys.put("java_runtime_version", System.getProperty("java.runtime.version"));
    
    String now = BASIC_DATE_FMT.format(new Date()) + " CET";

    sys.put("request_ts", now);
    
    //sys.put("PID", System.getProperty("PID"));
    sys.put("java_vm_vendor", System.getProperty("java.vm.vendor"));
    sys.put("java_home", System.getProperty("java.home"));
    sys.put("os_name", System.getProperty("os.name"));
    sys.put("os_version", System.getProperty("os.version"));
    sys.put("os_arch", System.getProperty("os.arch"));
    sys.put("user_name", System.getProperty("user.name"));
    sys.put("java_command", System.getProperty("sun.java.command"));
    sys.put("java_arch", System.getProperty("sun.arch.data.model"));
    
    List<Map<String,Object>> netinfo = new ArrayList<>();
    Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
    for (NetworkInterface netint : Collections.list(nets)) {
        if ("lo".equals(netint.getDisplayName()) ) {
            continue;
        }     
        Map<String,Object> ni = new HashMap<>();
        ni.put("displayname", netint.getDisplayName());
        ni.put("name", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        int ifaceNr = 0;
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            ni.put("address"+(ifaceNr++), inetAddress);
        }
        netinfo.add(ni);
    }

    final Properties labels = new Properties();
    try (InputStream stream = DeployInfo.class.getResourceAsStream("/labels.properties")) {
        if (stream == null) {
            System.out.println("BOEH!");
        }
        else {   
            labels.load(stream);
        }
    }
   
    res.put("labels", labels); 	
    res.put("net", netinfo);
    res.put("system",sys);
    
    return res;
  }

}
