/**
 * Curated high-quality Dojo rounds with native translations for instant 0ms load.
 */
import type { DojoRound } from '../ai/engine';
import type { DojoBrief } from '../ai/prompts';

interface LocalizedContent {
  message: string;
  explanation: string;
  tells: string[];
}

interface CuratedDojoItem {
  sender: string;
  channel: string;
  isScam: boolean;
  theme: string;
  message: string;
  explanation: string;
  tells: string[];
  localized: Record<string, LocalizedContent>;
}

export const CURATED_DOJO_ROUNDS: CuratedDojoItem[] = [
  {
    sender: '+1 (530) 555-0142',
    channel: 'text message',
    isScam: true,
    theme: 'a courier parcel held by customs in India',
    message: 'India Post: Your parcel is on hold due to an incomplete address. Update within 12 hours to avoid return: https://indiapost-redelivery-help.top/track',
    explanation: 'Delivery companies do not text you payment or address links from personal numbers. Check tracking in the official app.',
    tells: ['".top" domain that is not indiapost.gov.in', '12-hour deadline', 'Unexpected text from a random number'],
    localized: {
      Hindi: {
        message: 'इंडिया पोस्ट: आपका पार्सल पते की कमी के कारण रुका हुआ है। पार्सल वापस होने से बचाने के लिए 12 घंटे में अपडेट करें: https://indiapost-redelivery-help.top/track',
        explanation: 'डाक विभाग कभी भी व्यक्तिगत मोबाइल नंबर से पते या भुगतान के लिए लिंक नहीं भेजता। हमेशा आधिकारिक वेबसाइट indiapost.gov.in पर देखें।',
        tells: ['फर्जी .top लिंक जो indiapost.gov.in नहीं है', '12 घंटे की झूठी समय सीमा', 'अज्ञात नंबर से मैसेज'],
      },
      Bengali: {
        message: 'ইন্ডিয়া পোস্ট: অসম্পূর্ণ ঠিকানার কারণে আপনার পার্সেল আটকে আছে। ফেরত এড়াতে ১২ ঘণ্টার মধ্যে আপডেট করুন: https://indiapost-redelivery-help.top/track',
        explanation: 'ডাক বিভাগ কখনো ব্যক্তিগত নম্বর থেকে লিঙ্ক পাঠায় না। সর্বদা অফিসিয়াল indiapost.gov.in ওয়েবসাইটে ট্র্যাক করুন।',
        tells: ['ভুয়া .top লিঙ্ক', '১২ ঘণ্টার চাপ সৃষ্টি করা সময়সীমা', 'অপরিচিত নম্বর থেকে আসা মেসেজ'],
      },
      Tamil: {
        message: 'இந்தியா போஸ்ட்: முகவரி முழுமையடையாததால் உங்கள் பார்சல் நிறுத்தி வைக்கப்பட்டுள்ளது. திரும்புவதைத் தவிர்க்க 12 மணி நேரத்திற்குள் புதுப்பிக்கவும்: https://indiapost-redelivery-help.top/track',
        explanation: 'அஞ்சல் துறை ஒருபோதும் தனிப்பட்ட எண்களிலிருந்து கட்டணம் அல்லது முகவரி இணைப்புகளை அனுப்பாது. அதிகாரப்பூர்வ indiapost.gov.in தளத்தில் சரிபார்க்கவும்.',
        tells: ['அதிகாரப்பூர்வமற்ற .top இணைப்பு', '12 மணி நேர அவசர கெடு', 'தெரியாத எண்ணிலிருந்து வந்த மெசேஜ்'],
      },
      Telugu: {
        message: 'ఇండియా పోస్ట్: చిరునామా సరిగ్గా లేకపోవడం వల్ల మీ పార్శిల్ ఆపివేయబడింది. వెనక్కి వెళ్లకుండా ఉండటానికి 12 గంటల్లో అప్‌డేట్ చేయండి: https://indiapost-redelivery-help.top/track',
        explanation: 'తపాలా శాఖ ఎప్పుడూ వ్యక్తిగత నంబర్ల నుండి లింక్‌లను పంపదు. ఎల్లప్పుడూ అధికారిక indiapost.gov.in సైట్‌ను చూడండి.',
        tells: ['అధికారికం కాని .top లింక్', '12 గంటల తప్పుడు గడువు', 'తెలియని నంబర్ నుండి మెసేజ్'],
      },
      Marathi: {
        message: 'इंडिया पोस्ट: अपूर्ण पत्त्यामुळे तुमचे पार्सल थांबवले आहे. परत जाणे टाळण्यासाठी १२ तासांत अपडेट करा: https://indiapost-redelivery-help.top/track',
        explanation: 'टपाल खाते कधीही वैयक्तिक क्रमांकावरून लिंक पाठवत नाही. अधिकृत indiapost.gov.in वर ट्रॅक करा.',
        tells: ['अधिकृत नसलेली .top बनावट लिंक', '१२ तासांची खोटी मुदत', 'अज्ञात नंबरवरून आलेला मेसेज'],
      },
    },
  },
  {
    sender: 'BP-DHBVN',
    channel: 'text message',
    isScam: true,
    theme: 'an Indian electricity bill',
    message: 'Dear customer, your electricity power will disconnect tonight at 9:30 PM because previous month bill was not updated. Please immediately contact electricity officer 9876543210. Thanks.',
    explanation: 'Electricity boards never disconnect power without advance written notices or ask you to call personal mobile numbers.',
    tells: ['Threat of imminent power cut tonight', 'Personal 10-digit mobile number', 'Urgent panic tactic'],
    localized: {
      Hindi: {
        message: 'प्रिय उपभोक्ता, आपके पिछले महीने के बिजली बिल का भुगतान न होने के कारण आज रात 9.30 बजे बिजली काट दी जाएगी। तुरंत बिजली अधिकारी 9876543210 से संपर्क करें। धन्यवाद।',
        explanation: 'बिजली बोर्ड कभी भी व्यक्तिगत फोन नंबर से बिजली काटने की धमकी वाले एसएमएस नहीं भेजते। हमेशा बिजली विभाग के आधिकारिक बिल पोर्टल से जाँच करें।',
        tells: ['आज रात बिजली काटने की सीधी धमकी', 'व्यक्तिगत 10 अंकों का मोबाइल नंबर', 'घबराहट पैदा करने का हथकंडा'],
      },
      Bengali: {
        message: 'প্রিয় গ্রাহক, আগের মাসের বিল আপডেট না থাকায় আজ রাত ৯:৩০ এ আপনার বিদ্যুৎ সংযোগ বিচ্ছিন্ন করা হবে। অবিলম্বে বিদ্যুৎ কর্মকর্তা 9876543210 এর সাথে যোগাযোগ করুন। ধন্যবাদ।',
        explanation: 'বিদ্যুৎ পর্ষদ কখনো ব্যক্তিগত মোবাইল নম্বরে কল করতে বলে না বা নোটিশ ছাড়া লাইন কাটার হুমকি দেয় না।',
        tells: ['আজ রাতেই বিদ্যুৎ কাটার হুমকি', 'ব্যক্তিগত মোবাইল নম্বর', 'আতঙ্ক সৃষ্টির কৌশল'],
      },
      Tamil: {
        message: 'அன்புள்ள வாடிக்கையாளரே, கடந்த மாத பில் புதுப்பிக்கப்படாததால் இன்றிரவு 9:30 மணிக்கு உங்கள் மின் இணைப்பு துண்டிக்கப்படும். உடனடியாக மின் அதிகாரி 9876543210-ஐத் தொடர்பு கொள்ளவும்.',
        explanation: 'மின்சார வாரியம் ஒருபோதும் தனிப்பட்ட மொபைல் எண்களுக்கு அழைக்கச் சொல்லாது அல்லது அவசர மிரட்டல் விடுக்காது.',
        tells: ['இன்றிரவே மின்சாரம் துண்டிக்கப்படும் என்ற மிரட்டல்', 'தனிப்பட்ட 10 இலக்க மொபைல் எண்', 'பயமுறுத்தும் உத்தி'],
      },
      Telugu: {
        message: 'ప్రియమైన వినియోగదారుడా, మునుపటి నెల బిల్లు అప్‌డేట్ కానందున ఈ రాత్రి 9:30 గంటలకు మీ విద్యుత్ కనెక్షన్ కట్ చేయబడుతుంది. వెంటనే విద్యుత్ అధికారి 9876543210ని సంప్రదించండి.',
        explanation: 'విద్యుత్ శాఖ ఎప్పుడూ వ్యక్తిగత మొబైల్ నంబర్లకు కాల్ చేయమని లేదా అకస్మాత్తుగా కరెంట్ కట్ చేస్తామని మెసేజ్ చేయదు.',
        tells: ['ఈ రాత్రే కరెంట్ కట్ చేస్తామనే బెదిరింపు', 'వ్యక్తిగత మొబైల్ నంబర్', 'భయాందోళన కలిగించే వ్యూహం'],
      },
      Marathi: {
        message: 'प्रिय ग्राहक, मागील महिन्याचे वीज बिल अपडेट न झाल्यामुळे आज रात्री ९:३० वाजता वीज खंडित केली जाईल. कृपया लगेच वीज अधिकारी 9876543210 यांच्याशी संपर्क साधा.',
        explanation: 'वीज महामंडळ कधीही वैयक्तिक मोबाईल क्रमांकावर कॉल करायला सांगत नाही किंवा अशी तडकाफडकी धमकी देत नाही.',
        tells: ['आज रात्रीच वीज कापण्याची धमकी', 'वैयक्तिक १० अंकी मोबाईल नंबर', 'घबराट पसरवण्याची युक्ती'],
      },
    },
  },
  {
    sender: '+91 91234 56789',
    channel: 'WhatsApp message',
    isScam: true,
    theme: 'a bank KYC update in India',
    message: 'SBI Alert: Your YONO account has been blocked due to pending KYC. Tap http://sbi-kyc-update.apk to update PAN and unblock immediately.',
    explanation: 'Banks never send APK download links or ask you to update KYC via third-party web addresses.',
    tells: ['Asks to download an APK file', 'Suspicious link that is not sbi.co.in', 'Artificial account block threat'],
    localized: {
      Hindi: {
        message: 'SBI अलर्ट: आपका YONO खाता लंबित KYC के कारण ब्लॉक कर दिया गया है। पैन कार्ड अपडेट कर तुरंत खाता चालू करने के लिए यहाँ टैप करें: http://sbi-kyc-update.apk',
        explanation: 'बैंक कभी भी व्हाट्सएप पर APK डाउनलोड करने का लिंक नहीं भेजते। यह फोन हैक करने का खतरनाक फ्रॉड है।',
        tells: ['APK फ़ाइल डाउनलोड करने को कहना', 'फर्जी लिंक जो sbi.co.in नहीं है', 'खाता ब्लॉक करने की झूठी धमकी'],
      },
      Bengali: {
        message: 'SBI সতর্কতা: পেন্ডিং KYC-এর কারণে আপনার YONO অ্যাকাউন্ট ব্লক করা হয়েছে। প্যান আপডেট করতে এবং অবিলম্বে আনব্লক করতে ট্যাপ করুন: http://sbi-kyc-update.apk',
        explanation: 'ব্যাংক কখনোই APK ফাইল ডাউনলোড করার লিঙ্ক পাঠায় না। এটি ফোন হ্যাক করার ফাঁদ।',
        tells: ['APK ফাইল ডাউনলোডের অনুরোধ', 'সন্দেহজনক ভুয়া লিঙ্ক', 'অ্যাকাউন্ট ব্লকের ভীতি প্রদর্শন'],
      },
      Tamil: {
        message: 'SBI எச்சரிக்கை: நிலுவையில் உள்ள KYC காரணமாக உங்கள் YONO கணக்கு முடக்கப்பட்டுள்ளது. PAN-ஐப் புதுப்பித்து உடனே இயக்க இங்கே தட்டவும்: http://sbi-kyc-update.apk',
        explanation: 'வங்கிகள் ஒருபோதும் APK பதிவிறக்க இணைப்புகளை அனுப்பாது. இது ஆபத்தான செயலி மோசடி.',
        tells: ['APK பதிவிறக்க கோரிக்கை', 'sbi.co.in அல்லாத போலி இணைப்பு', 'கணக்கு முடக்க அச்சுறுத்தல்'],
      },
      Telugu: {
        message: 'SBI హెచ్చరిక: పెండింగ్ KYC కారణంగా మీ YONO ఖాతా బ్లాక్ చేయబడింది. పాన్ కార్డ్ అప్‌డేట్ చేసి వెంటనే అన్‌బ్లాక్ చేయడానికి ట్యాప్ చేయండి: http://sbi-kyc-update.apk',
        explanation: 'బ్యాంకులు ఎప్పుడూ APK ఫైల్స్ డౌన్‌లోడ్ చేయమని లింకులు పంపవు. ఇది హ్యాకింగ్ మోసం.',
        tells: ['APK ఫైల్ డౌన్‌లోడ్ కోరడం', 'sbi.co.in కాని అనుమానాస్పద లింక్', 'ఖాతా బ్లాక్ బెదిరింపు'],
      },
      Marathi: {
        message: 'SBI अलर्ट: प्रलंबित KYC मुळे तुमचे YONO खाते ब्लॉक करण्यात आले आहे. पॅन अपडेट करून त्वरित अनब्लॉक करण्यासाठी टॅप करा: http://sbi-kyc-update.apk',
        explanation: 'बँका कधीही APK डाउनलोड लिंक पाठवत नाहीत. हे फोन हॅक करण्याचे षड्यंत्र असते.',
        tells: ['APK फाइल डाउनलोड करण्यास सांगणे', 'sbi.co.in नसलेली बनावट लिंक', 'खाते ब्लॉक करण्याची धमकी'],
      },
    },
  },
  {
    sender: 'Swiggy',
    channel: 'text message',
    isScam: false,
    theme: 'an online marketplace sale',
    message: '482910 is your Swiggy OTP for login. Never share your OTP with anyone, including Swiggy delivery partners.',
    explanation: 'Direct verification code requesting no action other than entering it into the official app, explicitly warning against sharing.',
    tells: ['Official sender header', 'Explicit warning not to share OTP', 'No hyperlinks or phone numbers to call'],
    localized: {
      Hindi: {
        message: '482910 आपका Swiggy लॉगिन OTP है। अपना OTP किसी के साथ साझा न करें, डिलीवरी पार्टनर के साथ भी नहीं।',
        explanation: 'यह एक सुरक्षित और असली लॉगिन कोड है। इसमें कोई लिंक नहीं है और स्पष्ट रूप से ओटीपी किसी को न देने की चेतावनी है।',
        tells: ['आधिकारिक कंपनी हेडर', 'OTP न बांटने की स्पष्ट चेतावनी', 'कोई फर्जी लिंक या नंबर नहीं'],
      },
      Bengali: {
        message: '482910 হলো আপনার Swiggy লগইন OTP। ডেলিভারি পার্টনার সহ কারও সাথে আপনার OTP শেয়ার করবেন না।',
        explanation: 'এটি আসল লগইন ওটিপি। কোনো লিঙ্ক নেই এবং ওটিপি শেয়ার না করার পরিষ্কার নির্দেশ রয়েছে।',
        tells: ['অফিসিয়াল প্রেরক হেডার', 'OTP শেয়ার না করার সতর্কতা', 'কোনো ক্ষতিকারক লিঙ্ক নেই'],
      },
      Tamil: {
        message: '482910 என்பது உங்கள் Swiggy உள்நுழைவு OTP. டெலிவரி பார்ட்னர்கள் உட்பட யாருடனும் உங்கள் OTP-யைப் பகிர வேண்டாம்.',
        explanation: 'இது உண்மையான உள்நுழைவு குறியீடு. இதில் எந்த இணைப்பும் இல்லை மற்றும் OTP-யைப் பகிர வேண்டாம் என்று தெளிவாகக் கூறுகிறது.',
        tells: ['அதிகாரப்பூர்வ அனுப்புநர்', 'OTP பகிரக்கூடாது என்ற தெளிவான எச்சரிக்கை', 'சந்தேகத்திற்குரிய லிங்க் இல்லை'],
      },
      Telugu: {
        message: '482910 అనేది మీ Swiggy లాగిన్ OTP. డెలివరీ భాగస్వాములతో సహా ఎవరితోనూ మీ OTPని పంచుకోవద్దు.',
        explanation: 'ఇది నిజమైన లాగిన్ కోడ్. ఇందులో ఎటువంటి లింక్‌లు లేవు మరియు OTPని ఎవరికీ ఇవ్వవద్దని స్పష్టంగా హెచ్చరిస్తుంది.',
        tells: ['అధికారిక సంస్థ పేరు', 'OTP పంచుకోవద్దని స్పష్టమైన హెచ్చరిక', 'ఎలాంటి తప్పుడు లింక్ లేదు'],
      },
      Marathi: {
        message: '482910 हा तुमचा Swiggy लॉगिन OTP आहे. डिलिव्हरी पार्टनरसह कोणाशीही तुमचा OTP शेअर करू नका.',
        explanation: 'हा खरा लॉगिन कोड आहे. यात कोणतीही लिंक नाही आणि OTP गुप्त ठेवण्याची स्पष्ट सूचना आहे.',
        tells: ['अधिकृत पाठवणारा', 'OTP शेअर न करण्याचा स्पष्ट इशारा', 'कोणतीही संशयास्पद लिंक नाही'],
      },
    },
  },
  {
    sender: 'Daily Earn HR',
    channel: 'WhatsApp message',
    isScam: true,
    theme: 'a job opportunity',
    message: 'Earn Rs 3000-5000 daily working from home! Simply like YouTube videos and review products. Message our HR manager on Telegram: @earn_daily_india',
    explanation: 'Classic task fraud scam. Scammers pay small sums initially to gain trust, then trick victims into depositing large amounts.',
    tells: ['Unrealistic daily pay for simple tasks', 'Redirects to Telegram for chat', 'Unsolicited job offer'],
    localized: {
      Hindi: {
        message: 'घर बैठे रोज़ ₹3000-5000 कमाएं! बस YouTube वीडियो लाइक करें और रेटिंग दें। हमारे HR मैनेजर से Telegram पर बात करें: @earn_daily_india',
        explanation: 'यह क्लासिक "टास्क स्कैम" है। पहले छोटे पैसे देकर विश्वास जीतते हैं, फिर प्रीपेड टास्क के नाम पर लाखों रुपये ठग लेते हैं।',
        tells: ['आसान काम के लिए अवास्तविक रोज़ाना कमाई', 'टेलीग्राम पर चैट करने का दबाव', 'अवांछित नौकरी का लालच'],
      },
      Bengali: {
        message: 'ঘরে বসে প্রতিদিন ৩০০০-৫০০০ টাকা আয় করুন! শুধু ইউটিউব ভিডিও লাইক করুন। টেলিগ্রামে আমাদের এইচআর ম্যানেজারের সাথে যোগাযোগ করুন: @earn_daily_india',
        explanation: 'এটি কুখ্যাত টাস্ক প্রতারণা। প্রথমে সামান্য টাকা দিয়ে ফাঁদে ফেলে, পরে লাখ লাখ টাকা হাতিয়ে নেয়।',
        tells: ['অবাস্তব দৈনিক আয়ের প্রলোভন', 'টেলিগ্রামে নিয়ে যাওয়ার চেষ্টা', 'অযাচিত চাকরির প্রস্তাব'],
      },
      Tamil: {
        message: 'வீட்டிலிருந்தே தினமும் ரூ. 3000-5000 சம்பாதிக்கலாம்! YouTube வீடியோக்களை லைக் செய்தால் போதும். Telegram-ல் எங்கள் HR மேலாளரைத் தொடர்பு கொள்ளவும்: @earn_daily_india',
        explanation: 'இது டாஸ்க் மோசடி. ஆரம்பத்தில் சிறிய தொகையைக் கொடுத்து நம்ப வைத்து, பின்னர் பெரும் தொகையைப் பறிப்பார்கள்.',
        tells: ['எளிய வேலைக்கு அதீத தினசரி வருமானம்', 'Telegram-ல் பேச அழைப்பது', 'கேட்காத வேலை வாய்ப்பு'],
      },
      Telugu: {
        message: 'ఇంట్లో కూర్చుని రోజూ రూ. 3000-5000 సంపాదించండి! కేవలం యూట్యూబ్ వీడియోలను లైక్ చేయండి. టెలిగ్రామ్‌లో మా HR మేనేజర్‌ను సంప్రదించండి: @earn_daily_india',
        explanation: 'ఇది ప్రసిద్ధ టాస్క్ ఫ్రాడ్. మొదట కొద్దిగా డబ్బు ఇచ్చి నమ్మకం కుదిర్చి, తర్వాత పెట్టుబడి పేరుతో భారీగా మోసం చేస్తారు.',
        tells: ['చిన్న పనికి అవాస్తవ రోజువారీ సంపాదన', 'టెలిగ్రామ్‌కు మళ్ళించడం', 'అడగని ఉద్యోగ ఆఫర్'],
      },
      Marathi: {
        message: 'घरी बसून दररोज ३०००-५००० रुपये कमवा! फक्त YouTube व्हिडिओ लाईक करा. Telegram वर आमच्या HR मॅनेजरशी संपर्क साधा: @earn_daily_india',
        explanation: 'हा प्रसिद्ध टास्क फ्रॉड आहे. सुरुवातीला थोडे पैसे देऊन विश्वास जिंकतात आणि नंतर मोठी फसवणूक करतात.',
        tells: ['सोप्या कामासाठी अवाजवी रोजची कमाई', 'टेलिग्रामवर चॅट करायला लावणे', 'अनाहूत नोकरीची ऑफर'],
      },
    },
  },
];

let lastIndex = -1;

export function getInstantDojoRound(language: string, avoidIdx = -1): DojoRound {
  let idx = Math.floor(Math.random() * CURATED_DOJO_ROUNDS.length);
  if (CURATED_DOJO_ROUNDS.length > 1 && (idx === lastIndex || idx === avoidIdx)) {
    idx = (idx + 1) % CURATED_DOJO_ROUNDS.length;
  }
  lastIndex = idx;
  const item = CURATED_DOJO_ROUNDS[idx];
  const loc = item.localized[language];

  const brief: DojoBrief = {
    isScam: item.isScam,
    theme: item.theme,
    channel: item.channel,
    language,
  };

  return {
    brief,
    sender: item.sender,
    message: loc ? loc.message : item.message,
    explanation: loc ? loc.explanation : item.explanation,
    tells: loc ? loc.tells : item.tells,
    source: 'on_device',
  };
}
