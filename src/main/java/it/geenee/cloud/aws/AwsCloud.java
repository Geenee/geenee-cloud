package it.geenee.cloud.aws;


import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import it.geenee.cloud.*;
import org.apache.commons.codec.binary.Hex;

import javax.net.ssl.SSLException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import it.geenee.cloud.http.HttpCloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AwsCloud extends HttpCloud {
	static Logger logger = LoggerFactory.getLogger(AwsCloud.class);

	public static final String DEFAULT_REGION = "us-east-1";
	public static final Configuration DEFAULT_CONFIGURATION = new Configuration(
			DEFAULT_REGION,
			null, // credentials
			60, // timeout
			3, // retry count
			8 * 1024 * 1024, // part size (must be more than 5MB for S3)
			5, // number of parallel threads
			""); // path prefix
	public static final String EC2_VERSION = "2015-10-01";

	protected static final String EC2_QUERY = "&Version=" + EC2_VERSION;


	/**
	 * Constructor
	 * @throws SSLException
	 */
	public AwsCloud() throws SSLException {
		this(createGobals(), null);
	}

	/**
	 * Constructor
	 * @param configBuilder global configuration
	 * @throws SSLException
	 */
	public AwsCloud(ConfigBuilder configBuilder) throws SSLException {
		this(createGobals(), configBuilder);
	}

	/**
	 * Constructor
	 * @param globals global instances that can be shared
	 * @param configBuilder global configuration
	 * @throws SSLException
	 */
	public AwsCloud(Globals globals, ConfigBuilder configBuilder) {
		super(globals, build(globals, DEFAULT_CONFIGURATION, configBuilder));
	}

	// helpers

	static Configuration build(HttpCloud.Globals globals, Configuration defaultConfiguration,
			ConfigBuilder configBuilder) {
		if (configBuilder == null)
			return defaultConfiguration;

		// iterate over credentials provider chain
		CredentialsProvider credentialsProvider = null;
		for (Object o : configBuilder.credientialsProviderChain) {
			if (o instanceof CredentialsProvider) {
				// credentials are directly given
				credentialsProvider = (CredentialsProvider) o;
				break;
			} else if (o instanceof User) {
				// try to get credentials for user in ~/.aws/credentials
				Credentials credentials = getCredentialsFromFile(((User) o).name);
				if (credentials != null) {
					credentialsProvider = new ConstantCredentialsProvider(credentials);
					break;
				}
			} else if (o instanceof InstanceRole) {
				try {
					credentialsProvider = new AwsInstanceCredentialsProvider(globals);
					break;
				} catch (Throwable e) {
					// something went wrong, try next provider
				}
			}
		}
		if (credentialsProvider == null && !configBuilder.credientialsProviderChain.isEmpty()) {
			// failed to obtain credentials
			throw new IllegalArgumentException("Unable to obtain credentials from provider chain " +
					configBuilder.credientialsProviderChain.toString());
		}

		return defaultConfiguration.merge(new Configuration(
				configBuilder.region,
				credentialsProvider,
				configBuilder.timeout,
				configBuilder.retryCount,
				configBuilder.partSize,
				configBuilder.channelCount,
				configBuilder.prefix));
	}

	// general

	@Override
	public Credentials getCredentials(String user) {
		return getCredentialsFromFile(user);
	}

	/**
	 * Get credentials for the given user from the ~/.aws/credentials file
	 * @param user name of IAM user
	 * @return credentials or null if not found
	 */
	public static Credentials getCredentialsFromFile(String user) {
		File credentialsPath = new File(System.getProperty("user.home"), ".aws/credentials");
		if (user == null)
			user = "default";

		String accessKey = null;
		String secretAccessKey = null;
		try (BufferedReader br = new BufferedReader(new FileReader(credentialsPath))) {
			String line;
			boolean getCredentials = false;
			while ((line = br.readLine()) != null) {
				line.trim();
				if (line.startsWith("[") && line.endsWith("]")) {
					// found a line containing an user
					if (getCredentials)
						break;
					getCredentials = user.equals(line.substring(1, line.length() - 1));
				} else if (getCredentials) {
					// credentials for requested user
					int eqPos = line.indexOf("=");
					if (eqPos != -1) {
						String key = line.substring(0, eqPos).trim();
						String value = line.substring(eqPos + 1).trim();

						switch (key) {
							case "aws_access_key_id":
								accessKey = value;
								break;
							case "aws_secret_access_key":
								secretAccessKey = value;
								break;
						}
					}
				}
			}
		} catch (IOException e) {
		}

		if (accessKey != null && secretAccessKey != null) {
			logger.info("using credentials from ~/.aws/credentials for user '{}'", user);
			return new Credentials(accessKey, secretAccessKey);
		}

		return null;
		//throw new IllegalArgumentException("No AWS profile named '" + user + '\'');
	}

	// this instance

	@Override
	public Future<InstanceInfo> startGetInstance() {
		return new AwsGetInstance(this.globals);
	}

	// compute

	@Override
	public AwsCompute getCompute(ConfigBuilder configBuilder) {
		// merge global configuration with given configuration
		Configuration configuration = build(this.globals, this.configuration, configBuilder);
		String host = getHost("ec2", configuration.region);
		return new AwsCompute(this, configuration, host);
	}

	// storage

	@Override
	public AwsStorage getStorage(ConfigBuilder configBuilder) {
		// merge global configuration with given configuration
		Configuration configuration = build(this.globals, this.configuration, configBuilder);
		String host = getHost("s3", configuration.region);
		return new AwsStorage(this, configuration, host);
	}

	// helpers

	String getHost(String service, String region) {
		if (region.equals(DEFAULT_REGION))
			return service + ".amazonaws.com";
		return service + '.' + region + ".amazonaws.com";
	}

	static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	/**
	 * Sign a request according to http://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
	 * Adds x-amz-content-sha256, x-amz-date and Authorization headers
	 * @param request netty request object
	 * @param configuration configuration (contains user credentials)
	 * @param contentSha256 sha-256 hash of content
	 * @throws Exception
	 */
	public void signRequest(HttpRequest request, byte[] contentSha256, Configuration configuration) throws Exception {
		boolean useQuery = false;
		HttpHeaders headers = request.headers();
		Credentials credentials = configuration.credentialsProvider.getCredentials();

		// hex encode SHA-256 hash of content and set header
		String contentSha256Hex = Hex.encodeHexString(contentSha256);
		headers.set("x-amz-content-sha256", contentSha256Hex);

		// get date and time and set header
		Date currentDate = new Date();
		String time = DATE_FORMAT.format(currentDate);
		String date = time.substring(0, 8);
		if (!useQuery)
			headers.set("x-amz-date", time);

		// set token to header
		if (credentials.token != null)
			headers.set("x-amz-security-token", credentials.token);

		// get request method (e.g. "GET")
		String method = request.getMethod().name();

		// get host
		String host = headers.get(HttpHeaders.Names.HOST);

		// get request path and query (e.g. "/myBucket/foo.txt?versionId=123")
		String pathAndQuery = request.getUri();

		// get service (e.g. "s3") from host
		String service = host.substring(0, host.indexOf('.'));


		// scope
		String scope = date + '/' + configuration.region + '/' + service + "/aws4_request";

		// credential
		String credential = credentials.accessKey + '/' + scope;

		// canonical headers (sorted, will be signed)
		TreeMap<String, String> canonicalHeaders = new TreeMap<>();
		for (Map.Entry<String, String> entry : headers.entries()) {
			canonicalHeaders.put(entry.getKey().toLowerCase(Locale.ENGLISH), entry.getValue());
		}

		// build signed headers string
		StringBuilder signedHeadersBuilder = new StringBuilder();
		for (Map.Entry<String, String> entry : canonicalHeaders.entrySet()) {
			if (signedHeadersBuilder.length() > 0)
				signedHeadersBuilder.append(';');
			String header = entry.getKey();
			signedHeadersBuilder.append(header);
		}
		String signedHeaders = signedHeadersBuilder.toString();


		if (useQuery) {
			// signature in query parameters
			pathAndQuery += (pathAndQuery.indexOf('?') == -1 ? '?' : '&') + "X-Amz-Algorithm=AWS4-HMAC-SHA256"
					+ "&X-Amz-Credential=" + encodeUrl(credential)
					+ "&X-Amz-Date=" + time
					+ "&X-Amz-Expires=600"
					+ "&X-Amz-SignedHeaders=" + encodeUrl(signedHeaders);
		}

		// split uri into path and canonical queries
		String path;
		String[] canonicalQueries;
		int queryPos = pathAndQuery.indexOf('?');
		if (queryPos == -1) {
			// no query parameters
			path = pathAndQuery;
			canonicalQueries = new String[0];
		} else {
			path = pathAndQuery.substring(0, queryPos);
			canonicalQueries = pathAndQuery.substring(queryPos + 1).split("&");
			Arrays.sort(canonicalQueries);
		}
		assert !path.isEmpty() : "path is empty, must be at least '/'";

		// build canonical request string (http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html)
		StringBuilder canonicalRequestBuilder = new StringBuilder();
		canonicalRequestBuilder.append(method).append('\n');
		canonicalRequestBuilder.append(path).append('\n');
		for (String query : canonicalQueries) {
			if (query != canonicalQueries[0])
				canonicalRequestBuilder.append('&');
			canonicalRequestBuilder.append(query);
			if (query.indexOf('=') == -1)
				canonicalRequestBuilder.append('=');
		}
		canonicalRequestBuilder.append('\n');
		for (Map.Entry<String, String> entry : canonicalHeaders.entrySet()) {
			String header = entry.getKey();
			canonicalRequestBuilder.append(header).append(':').append(entry.getValue()).append('\n');
		}
		canonicalRequestBuilder.append('\n');
		canonicalRequestBuilder.append(signedHeaders).append('\n');
		canonicalRequestBuilder.append(contentSha256Hex);
		String canonicalRequest = canonicalRequestBuilder.toString();

		// build string to sign
		String stringToSign = "AWS4-HMAC-SHA256\n" + time + '\n' + scope + '\n' + Hex.encodeHexString(sha256(canonicalRequest));

		// build signing key
		byte[] dateKey = sha256Mac("AWS4" + credentials.secretAccessKey, date);
		byte[] dateRegionKey = sha256Mac(dateKey, configuration.region);
		byte[] dateRegionServiceKey = sha256Mac(dateRegionKey, service);
		byte[] signingKey = sha256Mac(dateRegionServiceKey, "aws4_request");

		// build signature
		String signature = Hex.encodeHexString(sha256Mac(signingKey, stringToSign));

		// sign
		if (useQuery) {
			// query string
			String signed = pathAndQuery + "&X-Amz-Signature=" + signature;
			request.setUri(signed);
		} else {
			// authorization header
			String authorizationHeader = "AWS4-HMAC-SHA256 Credential=" + credential + ",SignedHeaders=" + signedHeaders + ",Signature=" + signature;
			headers.set(HttpHeaders.Names.AUTHORIZATION, authorizationHeader);
		}
	}

	@Override
	public void extendRequest(FullHttpRequest request, Configuration configuration) throws Exception {
		super.extendRequest(request, configuration);
		if (configuration.credentialsProvider != null) {
			// calc sha-256 of content
			ByteBuf content = request.content();
			byte[] data = content.array();
			int begin = content.readerIndex();
			int end = content.writerIndex();
			byte[] contentSha256 = sha256(data, begin, end - begin);

			// sign request
			signRequest(request, contentSha256, configuration);
		}
	}

	@Override
	public void extendRequest(HttpRequest request, FileChannel file, long offset, long length, Configuration configuration) throws Exception {
		super.extendRequest(request, file, offset, length, configuration);
		if (configuration.credentialsProvider != null) {
			// calc sha-256 of content file
			byte[] contentSha256 = sha256(file, offset, length);

			// sign request
			signRequest(request, contentSha256, configuration);
		}
	}

	@Override
	public int fail(String host, int statusCode, InputStream body) throws Exception {
		// get service (e.g. "s3") from host
		String service = host.substring(0, host.indexOf('.'));
		switch (service) {
		case "s3": {
				// http://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(S3Error.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				S3Error error = (S3Error) unmarshaller.unmarshal(body);

				// patch http status code according to s3 error code
				switch (error.code) {
				case "AccessDenied":
				case "ExpiredToken":
					statusCode = 403; // forbidden
					break;
				}

				logger.error("http: {}, s3: {}, message: {}", statusCode, error.code, error.message);
			}
			break;
		case "ec2": {
				// http://docs.aws.amazon.com/AWSEC2/latest/APIReference/errors-overview.html
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(Ec2Error.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				Ec2Error response = (Ec2Error) unmarshaller.unmarshal(body);

				// iterate over errors
				if (response.errors != null) {
					if (response.errors.errors != null) {
						for (Ec2Error.Error error : response.errors.errors) {
							// patch http status code according to ec2 error code
							switch (error.code) {
							case "RequestExpired":
								statusCode = 403; // forbidden
								break;
							}
							logger.error("http: {}, ec2: {}, message: {}", statusCode, error.code, error.message);
						}
					}
				}
			}
			break;
		}

		return statusCode;
	}

	@Override
	public String getHash(HttpHeaders headers) {
		return getHash(headers.get("ETag"));
	}
	public static String getHash(String eTag) {
		// remove quotes
		if (eTag.startsWith("\"") && eTag.endsWith("\""))
			return eTag.substring(1, eTag.length() - 1);
		return eTag;
	}

	@Override
	public String getVersion(HttpHeaders headers) {
		return headers.get("x-amz-version-id");
	}

	@Override
	public String addVersion(String urlPath, String version) {
		if (version == null)
			return urlPath;
		return HttpCloud.addQuery(urlPath, "versionId", version);
	}

}
