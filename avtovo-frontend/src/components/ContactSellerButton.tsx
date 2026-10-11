import {useState} from 'react';
import {Loader2, MessageCircle, Phone} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {getUserProfile} from '../api/users';

interface ContactSellerButtonProps {
    sellerId: string;
    /** "Napisz": opens the chat section at the bottom of the listing. */
    onWrite: () => void;
}

/** undefined = not fetched yet, null = seller has no phone number. */
type PhoneState = string | null | undefined;

export function ContactSellerButton({sellerId, onWrite}: ContactSellerButtonProps) {
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
            const seller = await getUserProfile(sellerId);
            setPhone(seller.phoneNumber?.trim() || null);
        } catch (err) {
            console.error(err);
            alert('Nie udało się pobrać numeru telefonu');
        } finally {
            setLoadingPhone(false);
        }
    };

    const callButton = phone ? (
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
