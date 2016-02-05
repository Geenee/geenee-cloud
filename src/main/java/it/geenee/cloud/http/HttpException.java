package it.geenee.cloud.http;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.*;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import it.geenee.cloud.*;


/**
 * HTTP excepton
 */
public class HttpException extends Exception {

	int code;

	public HttpException(int code) {
		super(code + ", " + getErrorDescription(code));
		this.code = code;
	}

	int getCode() {
		return this.code;
	}

	static String getErrorDescription(int code) {
		switch (code) {
			case 400:
				return "Bad Request";
			case 401:
				return "Unauthorized";
			case 403:
				return "Forbidden";
			case 404:
				return "Not Found";
			case 408:
				return "Request Time-out";
			case 411:
				return "Length Required";
			default:
				return "HTTP error";
		}
	}
}
