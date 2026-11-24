/**
 * Built-in example messages, scams and genuine ones, so nobody faces an empty box.
 */
export interface Sample {
  label: string;
  tone: 'scam' | 'suspicious' | 'safe';
  text: string;
}

export const SAMPLES: Sample[] = [
  {
    label: 'KYC update (India)',
    tone: 'scam',
    text: 'Dear Customer, your SBI YONO account will be BLOCKED today as your KYC/PAN is not updated. Update immediately: http://sbi-kyc-update.top/yono and share the OTP sent to your mobile to verify.',
  },
  {
    label: 'UPI refund (India)',
    tone: 'scam',
    text: 'Hello sir, I sent Rs 5,000 to your Google Pay by mistake, it was for my mother hospital bill. Please return it, I have sent you a collect request, just enter your UPI PIN to approve. Please help 🙏',
  },
  {
    label: 'Electricity bill (India)',
    tone: 'scam',
    text: 'Dear Consumer, your electricity power will be disconnected tonight at 9.30 PM because your previous month bill was not updated. Please immediately contact our electricity officer 9876543210. Thank you.',
  },
  {
    label: 'Toll fee text',
    tone: 'scam',
    text: 'E-ZPass: You have an unpaid toll balance of $4.15. To avoid a late fee of $50.00 and suspension of your license, pay within 24 hours: https://ezpass-tollpay.top/pay?id=88213\nReply Y then reopen this message to activate the link.',
  },
  {
    label: '"Hi Mum" new number',
    tone: 'scam',
    text: "Hi Mum it's me, I dropped my phone and this is my new number. Can't call right now, mic is broken. I need to pay a bill today before 5 or I get a penalty, can you send £780 and I'll pay you back Friday? Don't tell Dad yet x",
  },
  {
    label: 'Bank security alert',
    tone: 'scam',
    text: 'Chase Alert: We blocked a $1,249.00 purchase at BestBuy. If this was NOT you, call our fraud team now at +1 (888) 555-0199. Our agent will verify your identity with the one-time code we send you. Account no. 004417829',
  },
  {
    label: 'Job offer on WhatsApp',
    tone: 'suspicious',
    text: 'Hello! I am Linda from TalentBridge recruiting. Your profile matches a remote part-time role: 60-90 mins a day rating products, $300-$800/day. No experience needed. Interested? Add me on Telegram @linda_hr_tb',
  },
  {
    label: 'Real delivery update',
    tone: 'safe',
    text: 'Your Amazon order #113-4421987-2201 has been delivered to your front door. Track your package or report a problem in Your Orders at amazon.com/orders.',
  },
  {
    label: 'Dentist reminder',
    tone: 'safe',
    text: 'Hi Priya, a reminder of your appointment with Dr. Okafor at Brookside Dental on Tue 3 Dec at 10:30am. Reply C to confirm or call us on 020 7946 0321 to reschedule.',
  },
];
