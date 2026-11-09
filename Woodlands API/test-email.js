/* require('dotenv').config();

const formData = require('form-data');
const Mailgun = require('mailgun.js');

const mailgun = new Mailgun(formData);

const options = {
    username: 'api',
    key: process.env.MAILGUN_API_KEY
};

if (process.env.MAILGUN_REGION?.toLowerCase() === 'eu') {
    options.url = 'https://api.eu.mailgun.net';
}

const mg = mailgun.client(options);

async function test() {
    try {
        const result = await mg.messages.create(
            process.env.MAILGUN_DOMAIN,
            {
                from: `${process.env.MAILGUN_SENDER_NAME || 'Woodlands Designer Boards'} <${process.env.MAILGUN_SENDER_EMAIL}>`,
                to: ['kgamer3k@gamil.com'],
                subject: 'Woodlands Designer Boards - Mailgun Test',
                text: 'This is a test email from the Woodlands Designer Boards API.'
            }
        );

        console.log('Email sent successfully!');
        console.log(result);
    } catch (error) {
        console.error('Mailgun test failed:');
        console.error(error);
    }
}

test(); */