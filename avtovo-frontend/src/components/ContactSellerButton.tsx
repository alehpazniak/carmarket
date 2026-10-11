import {useState} from 'react';
import axios from 'axios';
import {Loader2, MessageCircle, Phone} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {getSellerPhone} from '../api/users';

interface ContactSellerButtonProps {
    carId: string;
    sellerId: string;
    /** The phone number is only available for ACTIVE listings, so "Zadzwoń" is hidden otherwise. */
    listingActive: boolean;
    /** "Napisz": opens the chat section at the bottom of the listing. */
    onWrite: () => void;
}

/** undefined = not fetched yet, null = no number to show (none given, or the backend refused). */
type PhoneState = string | null | undefined;

export function ContactSellerButton({carId, sellerId, listingActive, onWrite}: ContactSellerButtonProps) {
    const {user, isAuthenticated, loginWithGoogle} = useAuth();
    const [phone, setPhone] = useState<PhoneState>(undefined);
    const [loadingPhone, setLoadingPhone] = useState(false);

    // Don't let the seller message themselves
    if (user?.id === sellerId) return null;

    const showPhone = async () => {
        // Phone numbers are for logged-in users only; login returns to this page.
        if (!isAuthenticated) {
            loginWithGoogle();
            return;
        }
        setLoadingPhone(true);
        try {
            setPhone(await getSellerPhone(sellerId, carId));
        } catch (err) {
            console.error(err);
            alert(axios.isAxiosError(err) && err.response?.status === 429
                ? 'Zbyt wiele prób, spróbuj ponownie za chwilę'
                : 'Nie udało się pobrać numeru telefonu');
        } finally {
            setLoadingPhone(false);
        }
    };

    const callButton = !listingActive ? null : phone ? (
        <a className="contact-btn-outline" href={`tel:${phone.replace(/[^\d+]/g, '')}`}>
            <Phone size={18}/> {phone}
        </a>
    ) : phone === null ? (
        <span className="contact-phone-missing">Sprzedający nie podał numeru telefonu</span>
    ) : (
        <button className="contact-btn-outline" onClick={showPhone} disabled={loadingPhone}>
            {loadingPhone ? <Loader2 size={18} className="animate-spin"/> : <Phone size={18}/>} Zadzwoń
        </button>
    );

    return (
        <div className="contact-actions">
            <button className="contact-btn" onClick={isAuthenticated ? onWrite : loginWithGoogle}>
                <MessageCircle size={18}/> Napisz
            </button>
            {callButton}
        </div>
    );
}
