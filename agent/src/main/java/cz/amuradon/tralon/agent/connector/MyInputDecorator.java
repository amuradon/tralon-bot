package cz.amuradon.tralon.agent.connector;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.InputDecorator;

public class MyInputDecorator extends InputDecorator {

	@Override
	public InputStream decorate(IOContext ctxt, InputStream in) throws IOException {
		System.out.println("*** JSON input: InputStream ***");
		return new MyProxyInputStream(in);
	}

	@Override
	public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) throws IOException {
		System.out.println("*** JSON input: byte[] ***");
		System.out.println(new String(src));
		return new ByteArrayInputStream(src, offset, length);
	}

	@Override
	public Reader decorate(IOContext ctxt, Reader r) throws IOException {
		System.out.println("*** JSON input: Reader ***");
		return r;
	}

	private static final class MyProxyInputStream extends InputStream {
		
		StringBuilder builder;
		
		InputStream delegate;
		
		public MyProxyInputStream(InputStream delegate) {
			this.delegate = delegate;
			builder = new StringBuilder();
		}

	    public int read() throws IOException {
	    	System.out.println("*** InputStream.read()");
	    	return delegate.read();
	    }

	    public int read(byte[] b) throws IOException {
	    	System.out.println("*** InputStream.read(byte[])");
	        return delegate.read(b);
	    }

	    // XXX tohle funguje, ale tady nevim, jake REST API to je
	    public int read(byte[] b, int off, int len) throws IOException {
	    	System.out.println("*** InputStream.read(byte[],int,int)");
	    	int result = delegate.read(b, off, len);
	    	builder.append(new String(Arrays.copyOfRange(b, off, off + len)));
			return result;
	    }

	    public byte[] readAllBytes() throws IOException {
	    	System.out.println("*** InputStream.readAllBytes()");
	        return delegate.readAllBytes();
	    }

	    public byte[] readNBytes(int len) throws IOException {
	    	System.out.println("*** InputStream.readNBytes(int)");
	        return delegate.readNBytes(len);
	    }

	    public int readNBytes(byte[] b, int off, int len) throws IOException {
	    	System.out.println("*** InputStream.readNBytes(byte[],int,int)");
	        return delegate.readNBytes(b, off, len);
	    }

	    public long skip(long n) throws IOException {
	    	System.out.println("*** InputStream.skip(long)");
	        return delegate.skip(n);
	    }

	    public void skipNBytes(long n) throws IOException {
	    	System.out.println("*** InputStream.skipNBytes(long)");
	        delegate.skipNBytes(n);
	    }

	    public int available() throws IOException {
	    	System.out.println("*** InputStream.available()");
	        return delegate.available();
	    }

	    public void close() throws IOException {
	    	System.out.println("*** InputStream.close() " + builder.toString());
	    	delegate.close();
	    }

	    public void mark(int readlimit) {
	    	System.out.println("*** InputStream.mark(int)");
	    	delegate.mark(readlimit);
	    }

	    public void reset() throws IOException {
	    	System.out.println("*** InputStream.reset()");
	        delegate.reset();
	    }

	    public boolean markSupported() {
	    	System.out.println("*** InputStream.markSupported()");
	        return delegate.markSupported();
	    }

	    public long transferTo(OutputStream out) throws IOException {
	    	System.out.println("*** InputStream.transferTo(OutputStream)");
	       return delegate.transferTo(out);
	    }
		
	}
}