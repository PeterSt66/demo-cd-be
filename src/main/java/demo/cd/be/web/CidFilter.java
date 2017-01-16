package demo.cd.be.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Slf4j
public class CidFilter implements Filter {
  private static final Logger LOG = LoggerFactory.getLogger(CidFilter.class);

  private static final String CID = "cid";
    
  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {

    HttpServletRequest request = (HttpServletRequest) req;
    String cid = request.getHeader(CID);
    if (cid == null) {
        cid = "(missing)";
    }
    MDC.put(CID, cid);

    log.info("Incoming {} request for endpoint {}", request.getMethod(), request.getRequestURL());
    try {
        chain.doFilter(req, res);
    } 
    finally { 
        MDC.remove(CID); 
    } 
        
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig arg0) throws ServletException {}

}