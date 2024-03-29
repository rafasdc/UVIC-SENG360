import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class ClientOperation extends UnicastRemoteObject implements RMIClientInterface{
	
	protected ClientOperation() throws RemoteException {
		super();
	}
	
	private static RMIInterface look_up;
	static PublicKey serverPublicKey;
	private static PrivateKey privateKey;
	public static PublicKey publicKey;
	static SecretKey macKey;
	static byte[] macKeyBytes;
	public static boolean confidentiality, integrity, authentication = false;
	
	@Override
	public void sendMessageClientEncrypted(byte[] encryptedKey, byte[] encryptedText)
			throws RemoteException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException {
		
	    Cipher aesCipher = Cipher.getInstance("AES");
	    
	    SecretKey originalKey = decryptKey(encryptedKey);
		
		aesCipher.init(Cipher.DECRYPT_MODE, originalKey);
		// Decrypt the ciphertext
	    byte[] cleartext1 = aesCipher.doFinal(encryptedText);
	    String decryptedText = new String(cleartext1);
	    
	    System.out.println("Server: " + decryptedText);
		
	}


	@Override
	public void sendMessageClientIntegrity(String txt, byte[] macKey, byte[] macData)
			throws NoSuchAlgorithmException, InvalidKeyException, RemoteException {
		SecretKeySpec spec = new SecretKeySpec(macKey, "HmacMD5");
		Mac mac = Mac.getInstance("HmacMd5");
		
		mac.init(spec);
		mac.update(txt.getBytes());
		
		byte [] macCode = mac.doFinal();
		
		if (macCode.length != macData.length){
			System.out.println("ERROR: Integrity check failed, possible intercept");
		} else if (!Arrays.equals(macCode, macData)){
			System.out.println ("ERROR: Integrity check failed, possible intercept");
		} else {
			System.out.println("Server: " + txt);
		}
		
	}
	
	public void sendMessageClientEncryptedIntegrity(byte[] encryptedKey, byte[] encryptedText, byte[] macKey, byte[] macData) throws RemoteException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
		/* First decrypt the text to plaintext */
		Cipher aesCipher = Cipher.getInstance("AES");
	    
	    SecretKey originalKey = decryptKey(encryptedKey);
		
		aesCipher.init(Cipher.DECRYPT_MODE, originalKey);
		// Decrypt the ciphertext
	    byte[] cleartext1 = aesCipher.doFinal(encryptedText);
	    String decryptedText = new String(cleartext1);

	    /*Integrity check the decrypted text */
	    SecretKeySpec spec = new SecretKeySpec(macKey, "HmacMD5");
		Mac mac = Mac.getInstance("HmacMd5");
		
		mac.init(spec);
		mac.update(decryptedText.getBytes());
		
		byte [] macCode = mac.doFinal();
		
		if (macCode.length != macData.length){
			System.out.println("ERROR: Integrity check failed, possible intercept");
		} else if (!Arrays.equals(macCode, macData)){
			System.out.println ("ERROR: Integrity check failed, possible intercept");
		} else {
			System.out.println("System: " + decryptedText);
		}
	}

	@Override
	public PublicKey getPublicKey() throws RemoteException {
		return publicKey;
	}
	
	
	public static byte[] encryptKey(SecretKey key) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		String ciphertext = Base64.getEncoder().encodeToString(key.getEncoded());
		Cipher encryption = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		encryption.init(Cipher.PUBLIC_KEY, serverPublicKey);
		byte[] encryptedKey = encryption.doFinal(ciphertext.getBytes());
		return encryptedKey;
	}
	
	

	
	@Override
	public void sendMessageClient(String txt) throws RemoteException {
		System.out.println("Server: "+txt);
	}
	
	
	
	public static void main(String[] args) throws NotBoundException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, IOException {
		
		for (int i=0; i < args.length; i++){
			if (args[i].equals("c")){
				confidentiality = true;
			} else if (args[i].equals("i")){
				integrity = true;
			} else if (args[i].equals("a")){
				authentication = true;
			}
		}

		look_up = (RMIInterface) Naming.lookup("//localhost/MyServer");
		
		if (!securityFeaturesMatch()){
			System.out.println("Security Features: ");
			printSecurityFeatures();
			System.out.println("Do not match servers... Exiting");
			System.exit(0);
		}
		
		
		RMIClientInterface client = new ClientOperation();
		generateKeys();
		look_up.registerClient(client);
		serverPublicKey = look_up.getPublicKey();
		
		System.out.println("Connection established with features:");
		printSecurityFeatures();
		
		
		if (authentication){
			int authenticate = 0;
			int tries = 0;
			final JPanel frame = new JPanel();
			while (authenticate != 1){
				String usr = JOptionPane.showInputDialog("Enter Username:");
				String pswd = JOptionPane.showInputDialog("Enter Password:");
				authenticate = look_up.authenticateClient(usr, pswd);
				if (authenticate != 1){
					JOptionPane.showMessageDialog(frame, "Incorrect user or password", "Inane error", JOptionPane.ERROR_MESSAGE);
				}
				tries++;
				if (tries > 3){
					System.out.println("Too many incorrect tries... Exiting");
					System.exit(0);
				}
			}
		}
		SecretKey key = generateKey();
		byte[] encodedKey = encryptKey(key);
		
		System.out.println("Initiate connection with server. Type a message:");
		while (true){
			
			Scanner sc = new Scanner(System.in);
			String txt = sc.nextLine();
			
			if (confidentiality && integrity){
				generateMACKey();
				byte [] ciphertext = encryptMessage(txt, key);
				look_up.sendMessageServerEncryptedIntegrity(encodedKey, ciphertext, macKeyBytes, generateMACData(txt));
			} else if (confidentiality){
				byte [] ciphertext = encryptMessage(txt, key);
				look_up.sendMessageServerEncrypted(encodedKey, ciphertext);
			} else if (integrity){
				generateMACKey();
				look_up.sendMessageServerIntegrity(txt, macKeyBytes, generateMACData(txt));
			} else {
				look_up.sendMessageServer(txt);
			}
		}
		

		
		
		


		


	}

	public static void showOptions(){
		JFrame f = new JFrame("Security Options");
		JCheckBox confidentiality = new JCheckBox("Confidentiality");
		confidentiality.setBounds(100,100,150,20);
		JCheckBox integrity = new JCheckBox("Integrity");
		integrity.setBounds(100,150,150,20); 
		JCheckBox authentication = new JCheckBox("Authentication");
		authentication.setBounds(100,200,150,20); 
		
		JButton button = new JButton("Ok");
		button.setBounds(100,250,150,20);
		
		
		f.add(confidentiality);
		f.add(integrity);
		f.add(authentication);
		f.add(button);
		f.setSize(400, 400);
		f.setLayout(null);
		f.setVisible(true);
		
		
		
	}

	
	private static void generateKeys() throws NoSuchAlgorithmException{
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
		
		KeyPair pair = keyGen.generateKeyPair();
		privateKey = pair.getPrivate();
		publicKey = pair.getPublic();
	}
	
	private SecretKey decryptKey(byte[] encryptedKey) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException{
		Cipher decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		decrypt.init(Cipher.PRIVATE_KEY, privateKey);
		byte[] decodedKey = decrypt.doFinal(encryptedKey);
		String decoded = new String (decodedKey);
		byte[] originalKey = Base64.getDecoder().decode(decoded);
		SecretKey decryptedKey = new SecretKeySpec(originalKey, 0, originalKey.length, "AES");
		return decryptedKey;
	}
	
	private static SecretKey generateKey() throws NoSuchAlgorithmException{
		KeyGenerator keygen = KeyGenerator.getInstance("AES");
		keygen.init(128);
	    SecretKey aesKey = keygen.generateKey();
	    return aesKey;
	}
	
	private static byte[] encryptMessage(String text, SecretKey aesKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
	    
	    Cipher aesCipher = Cipher.getInstance("AES");
	    
	    // Initialize the cipher for encryption
	    aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);

	    // Our cleartext
	    byte[] cleartext = text.getBytes();

	    // Encrypt the cleartext
	    byte[] ciphertext = aesCipher.doFinal(cleartext);
	    return ciphertext;
	}

	
	/* MAC FUNCTIONS */
	private static void generateMACKey() throws NoSuchAlgorithmException{
		KeyGenerator keygen = KeyGenerator.getInstance("HmacMD5");
		SecretKey macKeyGen = keygen.generateKey();
		macKey = macKeyGen;
		byte[] keyBytes = macKey.getEncoded();
		macKeyBytes = keyBytes;
	}
	
	private static byte[] generateMACData(String txt) throws NoSuchAlgorithmException, InvalidKeyException{
		Mac mac = Mac.getInstance("HmacMD5");
		mac.init(macKey);
		mac.update(txt.getBytes());
		byte[] macData = mac.doFinal();
		mac.reset();
		return macData;
	}
	
	/*Check Security Features match */
	private static boolean securityFeaturesMatch() throws RemoteException{
		if ((confidentiality && look_up.isConfidentialitySet() || !confidentiality && !look_up.isConfidentialitySet()) &&
				(integrity && look_up.isIntegritySet() || !integrity && !look_up.isIntegritySet()) && 
				(authentication && look_up.isAuthenticationSet() || !authentication && !look_up.isAuthenticationSet())){
			return true;
		} else {
			return false;
		}
	}
	
	private static void printSecurityFeatures(){
		if (confidentiality){
			System.out.println("Confidentiality");
		}
		if (integrity){
			System.out.println("Integrity");
		}
		if (authentication){
			System.out.println("Authentication");
		}
		if (!confidentiality && ! integrity && ! authentication){
			System.out.println("None");
		}
	}

}